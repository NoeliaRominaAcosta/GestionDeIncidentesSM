package com.oncall.engine.dashboard.web;

import com.oncall.engine.dashboard.service.DashboardService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import java.security.Principal;
import java.util.Map;

@Controller
public class DashboardController {

    @Autowired
    private DashboardService dashboardService;

    @GetMapping("/")
    public String index() {
        return "redirect:/dashboard";
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @GetMapping("/dashboard")
    public String dashboard(Model model, Principal principal) {
        Map<String, Object> metrics = dashboardService.getMetrics();
        metrics.forEach(model::addAttribute);
        
        if (principal != null) {
            model.addAttribute("currentUser", principal.getName());
        }
        
        return "dashboard";
    }
}
