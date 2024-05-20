package com.caching.demo.controller;

import com.caching.demo.model.Todo;
import com.caching.demo.service.DynamicTTLCacheService;
import com.caching.demo.service.TodoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@CrossOrigin(origins="*")
@RestController
@RequestMapping("/api/v1")
public class TodoController {
    @Autowired
    private TodoService todoService;

    @Autowired
    private DynamicTTLCacheService dynamicTTLCacheService;

    @GetMapping("/todos")
    public List<Todo> getAllTodos() {
        return todoService.getAllTodo();
    }

    @GetMapping("/todos/{id}")
    public Todo getTodoById(@PathVariable Long id) {

        Todo todo = (Todo) dynamicTTLCacheService.getValue("todo::"+id);
        if (todo == null){
            todo = todoService.getTodoById(id);
        }
        dynamicTTLCacheService.cacheValue("todo::"+ todo.getId(), todo, DynamicTTLCacheService.Priority.MEDIUM);
        return todo;

    }

    @PostMapping("/todos")
    public Todo createTodo(@RequestBody Todo todo) {
        todo = todoService.saveOrUpdateBook(todo);
        return todo;
    }

    @PutMapping("/todos/{id}")
    public ResponseEntity<Todo> updateTodo(@PathVariable Long id, @RequestBody Todo todo) {
        Todo existingTodo = todoService.getTodoById(id);
        if (existingTodo == null) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
        Todo updatedTodo = todoService.saveOrUpdateBook(todo);
        dynamicTTLCacheService.cacheValue("todo::"+updatedTodo.getId(), updatedTodo, DynamicTTLCacheService.Priority.MEDIUM);
        return new ResponseEntity<>(updatedTodo, HttpStatus.OK);
    }

    @DeleteMapping("/todos/{id}")
    public ResponseEntity<Void> deleteTodo(@PathVariable Long id) {
        Todo existingTodo = todoService.getTodoById(id);
        if (existingTodo == null) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
        dynamicTTLCacheService.deleteValue("todo::"+ id);
        todoService.deleteBook(id);
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }
}
