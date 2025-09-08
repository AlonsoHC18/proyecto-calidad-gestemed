package com.calidad.gestemed.controller;

import com.calidad.gestemed.domain.RolePolicy;
import com.calidad.gestemed.domain.User;
import com.calidad.gestemed.repo.RolePolicyRepo;
import com.calidad.gestemed.repo.UserRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

@Controller
@RequestMapping("/users")  // ruta general
@RequiredArgsConstructor
public class UserController {

    private final UserRepo userRepo;
    private final RolePolicyRepo roleRepo;

    @GetMapping
    public String list(Model model){
        model.addAttribute("users", userRepo.findAll());
        return "users/list";
    }

    @GetMapping("/new")
    public String form(Model model){
        model.addAttribute("user", new User());
        model.addAttribute("roles", roleRepo.findAll());
        return "users/edit";
    }

    @PostMapping
    public String save(@Valid @ModelAttribute("user") User user,
                       BindingResult result,
                       Model model){

        if(result.hasErrors()){
            model.addAttribute("roles", roleRepo.findAll());
            return "users/edit";
        }

        // Validación simple: correo único
        if(userRepo.findByEmail(user.getEmail()).isPresent()){
            result.rejectValue("email","error.user","El correo ya está en uso");
            model.addAttribute("roles", roleRepo.findAll());
            return "users/edit";
        }

        userRepo.save(user);
        return "redirect:/users";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id){
        userRepo.deleteById(id);
        return "redirect:/users";
    }
}
