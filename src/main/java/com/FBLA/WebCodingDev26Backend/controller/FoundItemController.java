package com.FBLA.WebCodingDev26Backend.controller;

import com.FBLA.WebCodingDev26Backend.dto.PublicFoundItemResponse;
import com.FBLA.WebCodingDev26Backend.model.FoundItem;
import com.FBLA.WebCodingDev26Backend.service.FoundItemService;
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
@RequestMapping("/api/items")
public class FoundItemController {
    private final FoundItemService service;

    public FoundItemController(FoundItemService service) {
        this.service = service;
    }

    @GetMapping
    public List<PublicFoundItemResponse> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    public PublicFoundItemResponse get(@PathVariable String id) {
        return service.getPublic(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public FoundItem create(@RequestBody Map<String, Object> data) {
        return service.create(data);
    }

    @PatchMapping("/{id}")
    public FoundItem update(@PathVariable String id, @RequestBody Map<String, Object> data) {
        return service.update(id, data);
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable String id) {
        return service.delete(id);
    }
}
