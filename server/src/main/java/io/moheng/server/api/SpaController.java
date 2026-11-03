package io.moheng.server.api;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class SpaController {

    @GetMapping({"/", "/projects", "/projects/**", "/analyses/**", "/history", "/settings"})
    public String index() {
        return "forward:/index.html";
    }
}
