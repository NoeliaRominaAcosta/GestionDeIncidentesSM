# RFC-001: Motor de Gestión de Incidentes On-Call (mini PagerDuty)

**Estado:** Draft
**Autor:** Romina Acosta
**Fecha:** 2026-07-18
**Stack propuesto:** Spring Boot, Spring State Machine, Spring MVC + Thymeleaf (SSR), Spring Data JPA, PostgreSQL, Spring Scheduler / SSM Timers

---

## 1. Resumen

Sistema backend SSR para gestionar el ciclo de vida de incidentes técnicos dentro de un esquema de guardias (on-call), con escalamiento automático cuando nadie responde a tiempo. El núcleo del sistema es una máquina de estados por incidente, modelada con Spring State Machine (SSM), que orquesta transiciones, temporizadores de escalamiento y notificaciones.

El objetivo no es solo un CRUD de incidentes: es demostrar manejo de **timers, guards, actions, persistencia de estado y regions** en un dominio con reglas de negocio reales.

---

## 2. Motivación

Los sistemas de guardia (PagerDuty, Opsgenie) resuelven un problema muy concreto: **si una persona no confirma que está atendiendo un incidente en tiempo X, hay que escalarlo a otra persona automáticamente**, sin intervención humana en el loop de decisión. Esto es un caso de uso natural para una máquina de estados con temporizadores, y prácticamente no se ve en proyectos de portfolio.

---

## 3. Alcance (v1)

**Incluido:**
- Alta de incidentes (manual, simulando la "detección")
- Escalamiento automático por inactividad (timers)
- Cadena de guardia (on-call schedule) simple, configurable
- Historial de transiciones auditable
- Vista SSR del incidente con timeline y contador de escalamiento en vivo
- Post-mortem obligatorio antes de cerrar

**Fuera de alcance (v1):**
- Integraciones reales (Slack, SMS, email) — se simulan con logs/eventos internos
- Autenticación compleja (alcanza con un login simple o usuarios hardcodeados)
- Multi-tenant

---

## 4. Modelo de estados

```
DETECTADO
   │  (auto, al crearse)
   ▼
TRIAGE ───────────────┐
   │ asignar()         │ timeout_triage (5 min sin asignar)
   ▼                    ▼
ASIGNADO            (vuelve a TRIAGE, escala a siguiente on-call)
   │ confirmar()       │ timeout_ack (2 min sin confirmar)
   ▼                    ▼
EN_MITIGACION        (vuelve a TRIAGE, escala)
   │ mitigar()
   ▼
MITIGADO
   │ requerir_postmortem()
   ▼
POST_MORTEM_PENDIENTE
   │ completar_postmortem()
   ▼
CERRADO
```

### 4.1 Estados

| Estado | Descripción |
|---|---|
| `DETECTADO` | Incidente recién creado, sin dueño. Estado transitorio, dispara TRIAGE automáticamente. |
| `TRIAGE` | Buscando quién lo atiende. Si nadie lo toma en `TRIAGE_TIMEOUT`, se re-triggerea la búsqueda con el siguiente en la cadena de guardia. |
| `ASIGNADO` | Un responsable fue asignado pero no confirmó ("ack"). Si no confirma en `ACK_TIMEOUT`, vuelve a `TRIAGE` y **se marca al responsable anterior como "missed"** (dato relevante para reportes de guardia). |
| `EN_MITIGACION` | El responsable confirmó y está trabajando en el incidente. Sin timeout de escalamiento por defecto (podría agregarse un "no news timeout" en v2). |
| `MITIGADO` | El impacto está resuelto pero falta el análisis. |
| `POST_MORTEM_PENDIENTE` | Esperando que se complete el documento de post-mortem (guard: no se puede cerrar sin postmortem si `severity >= SEV2`). |
| `CERRADO` | Estado final. |

### 4.2 Eventos

- `CREAR_INCIDENTE`
- `ASIGNAR` (manual o automático por el motor de escalamiento)
- `CONFIRMAR` (ack del responsable)
- `MITIGAR`
- `COMPLETAR_POSTMORTEM`
- `TIMEOUT_TRIAGE` (interno, disparado por SSM timer)
- `TIMEOUT_ACK` (interno, disparado por SSM timer)
- `REABRIR` (desde `CERRADO` vuelve a `EN_MITIGACION`, caso de regresión)

### 4.3 Guards relevantes

- `puedeAsignar`: el usuario a asignar debe estar `ACTIVE` en la guardia actual.
- `puedeCerrar`: si `severity` es SEV1 o SEV2, exige `postmortem != null`.
- `puedeEscalar`: verifica que exista un siguiente responsable en la cadena de guardia (si no hay, el incidente queda en `TRIAGE` con una alerta de "sin responsables disponibles", caso borde importante para el diseño).

### 4.4 Actions (side effects en transición)

- Al entrar a `TRIAGE`: iniciar timer `TRIAGE_TIMEOUT`, registrar intento de asignación en el historial.
- Al entrar a `ASIGNADO`: iniciar timer `ACK_TIMEOUT`, simular notificación al responsable.
- Al salir de `ASIGNADO` por timeout: marcar `missed_ack` en el historial, avanzar el índice de la cadena de guardia.
- Al entrar a `EN_MITIGACION`: cancelar timers pendientes, registrar `started_at` para métricas de MTTR.
- Al entrar a `MITIGADO`: calcular y persistir MTTR (mean time to resolve).
- Al entrar a `CERRADO`: liberar recursos, cerrar el timeline.

---

## 5. Escalamiento y guardia (on-call schedule)

Modelo simple para v1: una tabla `OnCallSchedule` con una lista ordenada de usuarios y un puntero al "actual". Cuando se dispara `TIMEOUT_ACK` o `TIMEOUT_TRIAGE`:

1. El listener de la SSM llama a `EscalationService.nextResponsible(schedule)`.
2. Si hay siguiente, dispara `ASIGNAR` con ese usuario.
3. Si no hay siguiente (se acabó la cadena), el incidente queda en `TRIAGE` con un flag `escalation_exhausted = true`, visible en la UI en rojo.

Esto evita loops infinitos y modela un caso real: a veces no hay a quién escalar.

---

## 6. Arquitectura técnica

### 6.1 Entidades principales

```
Incident
 - id, title, description, severity (SEV1..SEV4)
 - state (String, persistido por SSM)
 - createdAt, mitigatedAt, closedAt
 - currentAssignee (FK User, nullable)
 - onCallSchedule (FK)
 - escalationExhausted (boolean)

IncidentTransitionLog
 - id, incidentId, fromState, toState, event, triggeredBy (system|user), timestamp

OnCallSchedule
 - id, name, currentIndex

OnCallMember
 - id, scheduleId, user, order, status (ACTIVE|INACTIVE)

Postmortem
 - id, incidentId, rootCause, actionItems, author, createdAt
```

### 6.2 Persistencia de la State Machine

Usar `JpaPersistingStateMachineInterceptor` (o `RepositoryStateMachinePersist`) para persistir el estado del incidente en la entidad `Incident`, de forma que:
- Cada incidente tiene su propia instancia de máquina, recuperada por `incidentId` (patrón "state machine per aggregate", clave en SSM cuando hay múltiples instancias concurrentes en vez de una sola máquina global).
- Los timers deben sobrevivir a un reinicio del servidor: para eso conviene guardar `timeout_at` como timestamp en la entidad y tener un **scheduler propio (Spring `@Scheduled`)** que revise cada X segundos qué incidentes vencieron su timeout y dispare el evento correspondiente en la SSM — en vez de depender 100% del timer interno de SSM (que vive en memoria y no sobrevive un restart). Esto es un detalle de diseño importante que vale la pena remarcar en la implementación.

### 6.3 Capa SSR

- `IncidentController`: lista de incidentes activos (`/incidents`), detalle con timeline (`/incidents/{id}`), formulario de alta.
- Vista de detalle con:
  - Badge de estado actual
  - Countdown visual al próximo timeout (JS simple, solo cosmético — la lógica real vive en el backend)
  - Timeline de transiciones (de `IncidentTransitionLog`)
  - Botones de acción habilitados/deshabilitados según transiciones válidas desde el estado actual (consultando `stateMachine.getTransitions()`)
- Thymeleaf fragments para reusar el timeline y el badge de estado en la lista y el detalle.

### 6.4 Módulos del proyecto

```
com.oncall
 ├── incident/          (entidad, repo, servicio, controller)
 ├── statemachine/       (config SSM, listeners, guards, actions)
 ├── schedule/            (on-call schedule y escalamiento)
 ├── postmortem/
 └── web/                (controllers SSR, DTOs de vista)
```

---

## 7. Métricas a exponer (para que el proyecto luzca completo)

- **MTTA** (mean time to acknowledge): `ackedAt - assignedAt`
- **MTTR** (mean time to resolve): `mitigatedAt - createdAt`
- Cantidad de escalamientos por incidente (proxy de "qué tan bien responde el equipo")
- Ranking de "missed acks" por usuario (cuidado: esto es sensible en un equipo real, pero para portfolio está bien mostrarlo)

Vista simple `/dashboard` con estas métricas agregadas, buena excusa para practicar consultas JPQL/agregaciones.

---

## 8. Plan de trabajo sugerido (incremental)

1. **Fase 0** – Modelo de datos + entidades + repos, sin state machine (todo estado como String plano).
2. **Fase 1** – Configurar SSM con estados y eventos básicos, sin timers ni guards. Transiciones 100% manuales vía botones SSR.
3. **Fase 2** – Agregar guards (`puedeAsignar`, `puedeCerrar`) y actions (logging en `IncidentTransitionLog`).
4. **Fase 3** – Persistencia de la state machine por incidente (múltiples instancias concurrentes).
5. **Fase 4** – Timers de escalamiento (`TIMEOUT_TRIAGE`, `TIMEOUT_ACK`) + scheduler de respaldo para sobrevivir reinicios.
6. **Fase 5** – Cadena de guardia y `EscalationService`.
7. **Fase 6** – Dashboard de métricas.
8. **Fase 7 (opcional)** – Tests con `StateMachineTestPlanBuilder` de SSM para validar transiciones y guards de forma aislada.

---

## 9. Riesgos y decisiones abiertas

- **Timers en memoria vs. persistidos**: SSM no persiste temporizadores por defecto; hay que decidir entre confiar en el scheduler de respaldo (recomendado) o investigar extensiones de terceros.
- **Una instancia de SSM por incidente** puede ser costoso en memoria si hay miles de incidentes activos simultáneos; para v1 no es un problema real, pero vale la pena dejarlo anotado como "no escalable a nivel enterprise sin ajustes".
- **Reabrir incidentes cerrados**: decidir si permite volver a `EN_MITIGACION` o si un reabierto genera un incidente nuevo enlazado (más realista pero más complejo).

---

## 10. Próximos pasos

- [ ] Confirmar modelo de severidades y su impacto en guards
- [ ] Definir tiempos de timeout por severidad (SEV1 más agresivo que SEV4)
- [ ] Prototipar la config de SSM (enums de estados/eventos + `StateMachineConfigurer`)
- [ ] Armar el esqueleto de entidades y migraciones (Flyway/Liquibase)
