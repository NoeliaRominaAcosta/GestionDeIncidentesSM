-- ===================================================================
-- Inicialización de Datos Base (Carga Inicial para mini PagerDuty)
-- ===================================================================

-- 1. Usuarios de la plataforma (On-Call Engineers)
INSERT INTO app_user (username, full_name, role, missed_acks_count) VALUES ('romina', 'Romina Acosta', 'ADMIN', 0);
INSERT INTO app_user (username, full_name, role, missed_acks_count) VALUES ('juan', 'Juan Pérez', 'USER', 0);
INSERT INTO app_user (username, full_name, role, missed_acks_count) VALUES ('maria', 'María Gómez', 'USER', 0);
INSERT INTO app_user (username, full_name, role, missed_acks_count) VALUES ('lucas', 'Lucas Díaz', 'USER', 0);

-- 2. Cronogramas de Guardia (Schedules)
INSERT INTO on_call_schedule (id, name, current_index) VALUES (1, 'Guardia de Backend Primary', 0);
INSERT INTO on_call_schedule (id, name, current_index) VALUES (2, 'Guardia de Infraestructura', 0);

-- Ajustar secuencia para on_call_schedule
ALTER TABLE on_call_schedule ALTER COLUMN id RESTART WITH 3;

-- 3. Miembros asignados a las Guardias (Members)
-- Guardia de Backend (1): Juan (0) -> Maria (1) -> Lucas (2). Todos activos.
INSERT INTO on_call_member (id, schedule_id, username, member_order, status) VALUES (1, 1, 'juan', 0, 'ACTIVE');
INSERT INTO on_call_member (id, schedule_id, username, member_order, status) VALUES (2, 1, 'maria', 1, 'ACTIVE');
INSERT INTO on_call_member (id, schedule_id, username, member_order, status) VALUES (3, 1, 'lucas', 2, 'ACTIVE');

-- Guardia de Infraestructura (2): Maria (0) -> Lucas (1) -> Juan (Inactivo, no recibe alertas)
INSERT INTO on_call_member (id, schedule_id, username, member_order, status) VALUES (4, 2, 'maria', 0, 'ACTIVE');
INSERT INTO on_call_member (id, schedule_id, username, member_order, status) VALUES (5, 2, 'lucas', 1, 'ACTIVE');
INSERT INTO on_call_member (id, schedule_id, username, member_order, status) VALUES (6, 2, 'juan', 2, 'INACTIVE');

-- Ajustar secuencia para on_call_member
ALTER TABLE on_call_member ALTER COLUMN id RESTART WITH 7;
