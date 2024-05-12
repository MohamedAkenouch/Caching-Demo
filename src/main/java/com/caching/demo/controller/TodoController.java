package com.caching.demo.controller;

import com.caching.demo.model.Todo;
import com.caching.demo.service.TodoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@CrossOrigin(origins="*")
@RestController
public class TodoController {
    @Autowired
    private TodoService todoService;


    @GetMapping("/todos")
    public List<Todo> getAllTodo() {
        return todoService.getAllTodo();
    }

    @GetMapping("/todo/{id}")
    public Todo getTodo(@PathVariable Long id) {
        return todoService.getTodo(id);
    }

    @PostMapping("/todo/add")
    public Todo addTodo(@RequestBody Todo todo) {
        return todoService.addTodo(todo);
    }
}
