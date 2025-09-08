package com.calidad.gestemed.service.impl;

// service/impl/AssetServiceImpl.java

import com.calidad.gestemed.domain.Asset;
import com.calidad.gestemed.repo.AssetRepo;
import com.calidad.gestemed.service.AssetService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service @RequiredArgsConstructor
public class AssetServiceImpl implements AssetService {

    private final AssetRepo assetRepo;

    // para crear el activo
    @Override
    public Asset create(Asset a, String createdBy) {
        if (assetRepo.existsByAssetId(a.getAssetId()))
            throw new AssetAlreadyExistsException(a.getAssetId());
        a.setCreatedAt(LocalDateTime.now());
        a.setLastGpsAt(LocalDateTime.now());
        a.setCreatedBy(createdBy);
        return assetRepo.save(a);
    }

    // obtener la lista de los activos
    @Override public List<Asset> list() {
        return assetRepo.findAll();
    }

    // para guardar las imagenes del activo localmente
    // no se está usando
    // hay que traer aquí los métodos que guardan en azure blob para tener un código mucho más limpio y organizado

    private String saveFiles(List<MultipartFile> files) {
        if (files == null || files.isEmpty()) return null;
        try {
            Path base = Paths.get("uploads").toAbsolutePath().normalize();
            Files.createDirectories(base);
            return files.stream().filter(f->!f.isEmpty()).map(f -> {
                try {
                    String name = System.currentTimeMillis() + "_" + f.getOriginalFilename();
                    Path p = base.resolve(name);
                    f.transferTo(p.toFile());
                    return "files/" + name;
                } catch (Exception e) { throw new RuntimeException(e); }
            }).collect(Collectors.joining(","));
        } catch (Exception e) {
            throw new RuntimeException("No se pudieron guardar archivos", e);
        }
    }
}

