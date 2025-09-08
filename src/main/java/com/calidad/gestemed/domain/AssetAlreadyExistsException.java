package com.calidad.gestemed.domain;

public class AssetAlreadyExistsException extends RuntimeException {
    public AssetAlreadyExistsException(String assetId) {
        super("El ID de activo '" + assetId + "' ya existe en el sistema.");
    }
}
