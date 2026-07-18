package com.oncall.engine.incident.web;

import com.oncall.engine.incident.domain.Incident;
import com.oncall.engine.incident.domain.Severity;
import com.oncall.engine.incident.domain.State;
import com.oncall.engine.incident.repository.IncidentRepository;
import com.oncall.engine.incident.repository.IncidentTransitionLogRepository;
import com.oncall.engine.incident.service.IncidentService;
import com.oncall.engine.postmortem.repository.PostmortemRepository;
import com.oncall.engine.postmortem.service.PostmortemService;
import com.oncall.engine.schedule.repository.OnCallScheduleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;
import java.util.List;

@Controller
@RequestMapping("/incidents")
public class IncidentController {

    @Autowired
    private IncidentRepository incidentRepository;

    @Autowired
    private IncidentService incidentService;

    @Autowired
    private OnCallScheduleRepository scheduleRepository;

    @Autowired
    private IncidentTransitionLogRepository logRepository;

    @Autowired
    private PostmortemRepository postmortemRepository;

    @Autowired
    private PostmortemService postmortemService;

    /**
     * Lista los incidentes con filtros opcionales de estado y severidad.
     */
    @GetMapping
    public String list(Model model, Principal principal,
                       @RequestParam(required = false) State state,
                       @RequestParam(required = false) Severity severity) {
        List<Incident> incidents;
        if (state != null && severity != null) {
            incidents = incidentRepository.findAll().stream()
                    .filter(i -> i.getState() == state && i.getSeverity() == severity)
                    .toList();
        } else if (state != null) {
            incidents = incidentRepository.findAll().stream()
                    .filter(i -> i.getState() == state)
                    .toList();
        } else if (severity != null) {
            incidents = incidentRepository.findAll().stream()
                    .filter(i -> i.getSeverity() == severity)
                    .toList();
        } else {
            incidents = incidentRepository.findAll();
        }

        model.addAttribute("incidents", incidents);
        model.addAttribute("states", State.values());
        model.addAttribute("severities", Severity.values());
        model.addAttribute("selectedState", state);
        model.addAttribute("selectedSeverity", severity);
        model.addAttribute("currentUser", principal.getName());

        return "incidents/list";
    }

    /**
     * Muestra el formulario para reportar un nuevo incidente.
     */
    @GetMapping("/new")
    public String newIncidentForm(Model model, Principal principal) {
        model.addAttribute("severities", Severity.values());
        model.addAttribute("schedules", scheduleRepository.findAll());
        model.addAttribute("currentUser", principal.getName());
        return "incidents/new";
    }

    /**
     * Procesa la creación de un nuevo incidente.
     */
    @PostMapping("/new")
    public String createIncident(@RequestParam String title,
                                 @RequestParam String description,
                                 @RequestParam Severity severity,
                                 @RequestParam Long scheduleId,
                                 Principal principal) {
        Incident incident = incidentService.createIncident(title, description, severity, scheduleId, principal.getName());
        return "redirect:/incidents/" + incident.getId();
    }

    /**
     * Muestra el panel de control y detalle de un incidente.
     */
    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model, Principal principal) {
        Incident incident = incidentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Incidente no encontrado con ID: " + id));

        model.addAttribute("incident", incident);
        model.addAttribute("logs", logRepository.findByIncidentIdOrderByTimestampAsc(id));
        model.addAttribute("postmortem", postmortemRepository.findByIncidentId(id).orElse(null));
        model.addAttribute("currentUser", principal.getName());
        
        // El usuario logueado es el responsable actual del incidente
        boolean isAssignee = incident.getCurrentAssignee() != null && 
                             incident.getCurrentAssignee().getUsername().equals(principal.getName());
        model.addAttribute("isAssignee", isAssignee);

        return "incidents/detail";
    }

    @PostMapping("/{id}/ack")
    public String ack(@PathVariable Long id, Principal principal) {
        incidentService.acknowledge(id, principal.getName());
        return "redirect:/incidents/" + id;
    }

    @PostMapping("/{id}/mitigate")
    public String mitigate(@PathVariable Long id, Principal principal) {
        incidentService.mitigate(id, principal.getName());
        return "redirect:/incidents/" + id;
    }

    @PostMapping("/{id}/close")
    public String close(@PathVariable Long id, Principal principal) {
        try {
            incidentService.close(id, principal.getName());
        } catch (Exception e) {
            // Manejar error de validación del guard (la StateMachine rechazará si es SEV1/2 y falta postmortem)
        }
        return "redirect:/incidents/" + id;
    }

    @PostMapping("/{id}/reopen")
    public String reopen(@PathVariable Long id, Principal principal) {
        incidentService.reopen(id, principal.getName());
        return "redirect:/incidents/" + id;
    }

    @PostMapping("/{id}/postmortem")
    public String submitPostmortem(@PathVariable Long id,
                                   @RequestParam String rootCause,
                                   @RequestParam String actionItems,
                                   Principal principal) {
        postmortemService.createPostmortem(id, rootCause, actionItems, principal.getName());
        return "redirect:/incidents/" + id;
    }
}
