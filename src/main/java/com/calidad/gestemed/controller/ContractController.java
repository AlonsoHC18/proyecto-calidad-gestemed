package com.calidad.gestemed.controller;

import com.calidad.gestemed.domain.Contract;
import com.calidad.gestemed.repo.AssetRepo;
import com.calidad.gestemed.repo.ContractRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import java.util.HashSet;
import java.util.regex.Pattern;

@Controller
@RequiredArgsConstructor
@RequestMapping("/contracts")
public class ContractController {

    private final ContractRepo contractRepo;
    private final AssetRepo assetRepo;

    private static final Pattern EMAIL_PATTERN = 
        Pattern.compile("^[a-zA-Z0-9._%+-]+@[a-z0-9.-]+\\.[a-z]{2,6}$");

    @GetMapping
    public String list(Model model){
        model.addAttribute("contracts", contractRepo.findAll());
        return "contracts/list";
    }

    @GetMapping("/new")
    public String form(Model model){
        model.addAttribute("contract", new Contract());
        model.addAttribute("assets", assetRepo.findAll());
        return "contracts/new";
    }

    @PostMapping
    public String create(Contract c, 
                         @RequestParam(required=false) Long[] assetIds,
                         @RequestParam("clientEmail") String clientEmail,
                         Model model) {

        // Validar correo
        if (!EMAIL_PATTERN.matcher(clientEmail).matches()) {
            model.addAttribute("error", "Correo electrónico inválido.");
            model.addAttribute("contract", c);
            model.addAttribute("assets", assetRepo.findAll());
            return "contracts/new";
        }
        c.setClientEmail(clientEmail);

        // Inicializar activos
        c.setAssets(new HashSet<>());
        if (assetIds != null) {
            for(Long id: assetIds) {
                c.getAssets().add(assetRepo.findById(id).orElseThrow());
            }
        }

        contractRepo.save(c);
        return "redirect:/contracts?created";
    }
}
