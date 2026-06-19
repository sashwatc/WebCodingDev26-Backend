package com.FBLA.WebCodingDev26Backend.controller;

import com.FBLA.WebCodingDev26Backend.service.GenericEntityService;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/entities")
public class GenericEntityController {
    private final GenericEntityService service;

    public GenericEntityController(GenericEntityService service) {
        this.service = service;
    }

    @GetMapping("/{entityName}")
    public List<?> list(@PathVariable String entityName) {
        return service.list(entityName);
    }

    @PostMapping("/{entityName}")
    @ResponseStatus(HttpStatus.CREATED)
    public Object create(@PathVariable String entityName, @RequestBody Map<String, Object> data) {
        return service.create(entityName, data);
    }

    @PatchMapping("/{entityName}/{id}")
    public Object update(@PathVariable String entityName, @PathVariable String id, @RequestBody Map<String, Object> data) {
        return service.update(entityName, id, data);
    }

    @DeleteMapping("/{entityName}/{id}")
    public Map<String, Boolean> delete(@PathVariable String entityName, @PathVariable String id) {
        return Map.of("success", service.delete(entityName, id));
    }
}
