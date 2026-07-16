package com.assessment.controller;

import com.assessment.entity.User;
import com.assessment.repository.UserRepository;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.List;

@RestController
@RequestMapping("/api/students")
public class StudentController {

    private final UserRepository userRepository;

    public StudentController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    public List<User> getAllStudents() {
        return userRepository.findAll()
                .stream()
                .filter(user -> user.getRole() == User.Role.STUDENT)
                .toList();
    }
}