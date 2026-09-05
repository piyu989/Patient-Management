package com.auth.controller;

import com.auth.dto.UserDto;
import com.auth.entity.User;
import com.auth.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class UserController {

    @Autowired
    private UserService userService;

    @PostMapping("/saveuser")
    public ResponseEntity<?> saveUser(@RequestBody UserDto userDto){
        User user = userService.saveUser(userDto);
        if(user!=null){
            return ResponseEntity.ok(user);
        }else{
            return ResponseEntity.badRequest().body("User already exists");
        }
    }

    @GetMapping("/all")
    public ResponseEntity<?> getAllUsers(){
        return ResponseEntity.ok(userService.getAllUsers());
    }

    @GetMapping("/user")
    public ResponseEntity<?> fetchUserByUsername(@RequestParam String username){
        System.out.println("username: " + username);
        return ResponseEntity.ok(userService.fetchUserByUsername(username));
    }

}
