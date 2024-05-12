package com.caching.demo.service;

import com.caching.demo.model.Todo;
import com.caching.demo.repo.TodoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TodoService {
    @Autowired
    private TodoRepository todoRepository;

    public List<Todo> getAllTodo() {
        return todoRepository.findAll();
    }

    public Todo addTodo(Todo todo){
        todoRepository.save(todo);
        return todo;
    }

    public Todo getTodo(Long id){
        return todoRepository.findById(id).orElse(null);
    }
}
