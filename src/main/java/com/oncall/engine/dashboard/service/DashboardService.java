package com.oncall.engine.dashboard.service;

import com.oncall.engine.incident.domain.Incident;
import com.oncall.engine.incident.repository.IncidentRepository;
import com.oncall.engine.user.domain.AppUser;
import com.oncall.engine.user.repository.AppUserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class DashboardService {

    @Autowired
    private IncidentRepository incidentRepository;

    @Autowired
    private AppUserRepository userRepository;

    /**
     * Calcula métricas globales de MTTA, MTTR, escalamientos y fallas para el panel de control.
     */
    public Map<String, Object> getMetrics() {
        List<Incident> incidents = incidentRepository.findAll();
        List<AppUser> users = userRepository.findAll(Sort.by(Sort.Direction.DESC, "missedAcksCount"));

        long totalIncidents = incidents.size();
        long activeIncidents = incidents.stream()
                .filter(i -> {
                    String state = i.getState().name();
                    return state.equals("TRIAGE") || state.equals("ASIGNADO") || state.equals("EN_MITIGACION");
                })
                .count();

        long totalEscalations = incidents.stream().mapToLong(Incident::getEscalationCount).sum();

        // 1. Calcular MTTA (Mean Time to Acknowledge): ackedAt - assignedAt
        double totalMttaMinutes = 0;
        long mttaCount = 0;
        for (Incident i : incidents) {
            if (i.getAckedAt() != null && i.getAssignedAt() != null) {
                Duration duration = Duration.between(i.getAssignedAt(), i.getAckedAt());
                totalMttaMinutes += duration.toSeconds() / 60.0;
                mttaCount++;
            }
        }
        String formattedMtta = mttaCount > 0 ? formatDuration(totalMttaMinutes / mttaCount) : "N/D";

        // 2. Calcular MTTR (Mean Time to Resolve/Mitigate): mitigatedAt - createdAt
        double totalMttrMinutes = 0;
        long mttrCount = 0;
        for (Incident i : incidents) {
            if (i.getMitigatedAt() != null) {
                Duration duration = Duration.between(i.getCreatedAt(), i.getMitigatedAt());
                totalMttrMinutes += duration.toSeconds() / 60.0;
                mttrCount++;
            }
        }
        String formattedMttr = mttrCount > 0 ? formatDuration(totalMttrMinutes / mttrCount) : "N/D";

        Map<String, Object> metrics = new HashMap<>();
        metrics.put("totalIncidents", totalIncidents);
        metrics.put("activeIncidents", activeIncidents);
        metrics.put("totalEscalations", totalEscalations);
        metrics.put("mtta", formattedMtta);
        metrics.put("mttr", formattedMttr);
        metrics.put("leaderboard", users);

        return metrics;
    }

    private String formatDuration(double decimalMinutes) {
        long totalSeconds = Math.round(decimalMinutes * 60);
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        
        if (minutes > 0) {
            return minutes + "m " + seconds + "s";
        } else {
            return seconds + "s";
        }
    }
}
