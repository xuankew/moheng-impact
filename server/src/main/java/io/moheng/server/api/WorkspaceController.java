package io.moheng.server.api;

import io.moheng.server.service.WorkspaceService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import static io.moheng.server.api.Responses.data;

@RestController
@RequestMapping("/api/workspace")
public class WorkspaceController {

    private final WorkspaceService workspace;

    public WorkspaceController(WorkspaceService workspace) {
        this.workspace = workspace;
    }

    @GetMapping("/dirs")
    public Map<String, Object> directories(@RequestParam(name = "path", required = false) String path) {
        return data(workspace.listDirectories(path));
    }
}
