package com.calidad.gestemed.controller;

import com.calidad.gestemed.domain.Asset;
import com.calidad.gestemed.domain.AssetMovement;
import com.calidad.gestemed.repo.AssetMovementRepo;
import com.calidad.gestemed.repo.AssetRepo;
import com.calidad.gestemed.service.AssetService;
import com.calidad.gestemed.service.impl.AzureBlobService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.security.Principal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;


import org.springframework.web.bind.annotation.*;

import java.io.IOException;


import java.util.stream.Collectors;

// Este es el controlador web de Spring MVC para gestionar  los activos medicos
@Controller //esta es la anotacion del framework para decir que esta clase va a ser un controlador
@RequiredArgsConstructor                 // Lombok: genera un constructor con los final (inyección por constructor). Gracias a lombok no es necesario definir un constructor en la clase
@RequestMapping("/assets")               // Prefijo común para todas las rutas de este controlador
public class AssetController {

    // assetService, movementRepo, assetRepo y azureBlobService son las dependencias del controlador (inyectadas por Spring a través del constructor gracias a lombok)

    private final AssetService assetService;       // Lógica de negocio para activos

    //JPA Permite hacer busquedas a la base de datos sin tener que escribir codigo
    //Por ejemplo, en estas clases AssetMovementRepo y AssetRepo estaria encapsulada toda la logica que permite hacer llamados a la base de datos
    //Por ejemplo, si queremos buscar un activo por su id solo hacemos assetRepo.findById(1). Eso buscaria en la base de datos es activo con id=1.

    private final AssetMovementRepo movementRepo;  // Repositorio JPA para movimientos de activos
    private final AssetRepo assetRepo; // Repositorio JPA para activos


    private final AzureBlobService azureBlobService; // Servicio propio para subir archivos a Azure Blob

    // GET /assets -> lista de activos
    @GetMapping
    public String list(Model model){
        model.addAttribute("assets", assetService.list()); // Pasa la lista de activos a la vista
        return "assets/list";                               // Muestra la plantilla assets/list.html
    }

    // GET /assets/new -> muestra formulario de creación de un activo
    @GetMapping("/new")
    public String form(Model model){
        model.addAttribute("asset", new Asset()); // Se envia a la vista html el objeto vacío asset para enlazar el formulario
        return "assets/new";                      // Se muestra la plantilla assets/new.html
    }

    // POST /assets -> crea un activo nuevo
    @PostMapping
    public String create(Asset asset, @RequestParam("photos") List<MultipartFile> photos, Authentication auth){

        // En el parametro List<MultipartFile> photos vienen las fotos que el usuario agrego para el activo. Pero estas fotos estan en un formato extraño y se necesita tranformarlas con el metodo map
        //photos.stream() se utiliza para iterar las photos. En este momento las photos no estan transformadas
        //.filter(f -> !f.isEmpty()): El primer filtro se asegura de que solo pasen las fotos que no están vacías
        //El método map transforma cada foto en el flujo. f es cada foto.
        String photosUrls = photos.stream()
                .filter(f -> !f.isEmpty())
                .map(f -> {
                    try {
                        return azureBlobService.uploadFile(f); // Sube la foto a azure blob service y retorna URL pública/descargable
                    } catch (IOException e) {
                        System.out.println("[WARN] No se pudo subir la foto: " + e.getMessage());
                        return null; // Si una sube falla, devolvemos null para luego filtrarlo
                    }
                })
                .filter(url -> url != null)                   // Quitamos los null (subidas fallidas)
                .collect(Collectors.joining("|"));             // es para unir cada url de las foto separadas por | algo asi: "url1|url2|url3"

        // Guardamos el string de URLs en el activo
        asset.setPhotoPaths(photosUrls);

        // Creamos el activo registrando quién lo creó (si no hay auth, usamos 'admin' por defecto)
        try {
            assetService.create(asset, (auth != null ? auth.getName() : "admin"));
            return "redirect:/assets?created";
        } catch (com.calidad.gestemed.exception.AssetAlreadyExistsException e) {
            // Pasamos el mensaje de error a la vista
            model.addAttribute("error", e.getMessage());
            // Devolvemos al formulario con el objeto asset para que no pierda los datos ingresados
            model.addAttribute("asset", asset);
            return "assets/new";
        }

    // si visitan /{id}/movements, se redirige al historial filtrable de movimientos de los activos */
    @GetMapping("/{id}/movements")
    public String movements(@PathVariable Long id){
        return "redirect:/assets/" + id + "/history";
    }

    // si hacen peticion GET /assets/{id}/move es muestra el formulario para mover un activo de ubicación
    @GetMapping("/{id}/move")
    public String moveForm(@PathVariable Long id, Model model) {
        // Buscamos el activo o lanzamos excepción si no existe
        model.addAttribute("asset", assetRepo.findById(id).orElseThrow());
        return "assets/move"; // Vista con el formulario de movimiento
    }

    // si se hace petición POST /assets/{id}/move entonces se ejecuta el movimiento del activo (actualiza la ubicación + registra el movimiento)

    // el parametro Principal who se utiliza para obtener información sobre el usuario que ha iniciado sesión y está haciendo la petición
    @PostMapping("/{id}/move")
    public String doMove(@PathVariable Long id,
                         @RequestParam String toLocation,
                         @RequestParam Double toLocationLatitude,
                         @RequestParam Double toLocationLongitude,
                         @RequestParam(required=false) String note,
                         Principal who) {

        //Leemos el activo y guardamos "de dónde" viene
        Asset a = assetRepo.findById(id).orElseThrow();
        String from = a.getInitialLocation();
        Double fromLatitude = a.getLastLatitude();
        Double fromLongitude = a.getLastLongitude();

        //Actualizamos su "ubicación actual" y coordenadas
        a.setInitialLocation(toLocation);
        a.setLastLatitude(toLocationLatitude);
        a.setLastLongitude(toLocationLongitude);

        //Guardamos en la base de datos el cambio del activo
        assetRepo.save(a);

        //Registramos el movimiento en la tabla de movimientos
        //IMPORTANTE: el campo de la entidad se llama 'reason', así que guardamos la 'note' ahí.
        //El patrón de diseño builder permite crear el objeto de forma más sencilla sin necesidad de instanciar
        movementRepo.save(AssetMovement.builder()
                .asset(a)
                .fromLocation(from)
                .toLocation(toLocation)
                .fromLocationLatitude(fromLatitude)
                .fromLocationLongitude(fromLongitude)
                .toLocationLatitude(toLocationLatitude)
                .toLocationLongitude(toLocationLongitude)
                .reason(note) // Guardamos la nota en el campo correcto
                .performedBy(who!=null?who.getName():"system") // Quién hizo el movimiento es decir, el usuari que inició sesión
                .movedAt(LocalDateTime.now())                  // Cuándo se hizo
                .build());

        // Volvemos al historial del activo
        return "redirect:/assets/" + id + "/history";
    }

    // GET /assets/{id}/history -> historial con filtros opcionales (fecha desde, fecha hasta, ubicación)
    @GetMapping("/{id}/history")
    public String history(@PathVariable Long id,
                          @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate from,
                          @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate to,
                          @RequestParam(required=false) String location,
                          Model model) {

        // Si no hay 'from', usamos el año 0001 para incluir todos los registros
        LocalDateTime fromDate = (from == null)
                ? LocalDateTime.of(1, 1, 1, 0, 0)
                : from.atStartOfDay();

        // Si no hay 'to', usamos un máximo (año 9999).
        // Si sí hay 'to', sumamos un día para incluir TODO el día 'to' (rango [from, to+1))
        LocalDateTime toDate = (to == null)
                ? LocalDateTime.of(9999, 12, 31, 0, 0)
                : to.plusDays(1).atStartOfDay();

        // Patrón para filtrar por ubicación usando LIKE en SQL; si está vacío, se pasa null (sin filtro)
        // El método trim() elimina los espacios en blanco por si el usuario dejó espacios en el campo ubicación. Sería bueno validarlo en la vista también
        String pattern = (location == null || location.isBlank())
                ? null
                : "%" + location.trim() + "%";

        // Cargamos el activo y buscamos sus movimientos usando un query nativo de postgres con filtros
        var asset = assetRepo.findById(id).orElseThrow();

        // searchNative es una busqueda sql nativa de postgres para buscar los movimientos de un activo filtrados por fechas y ubicación
        var list  = movementRepo.searchNative(id, fromDate, toDate, pattern);

        // Variables para la vista (útiles para mantener los filtros elegidos por el usuario)
        model.addAttribute("asset", asset);

        // este "movs" que se envia a la vista es muy importante porque es la lista de movimientos de los activos
        model.addAttribute("movs", list);
        model.addAttribute("from", from);
        model.addAttribute("to", to);
        model.addAttribute("location", location);

        return "assets/history"; // Renderiza la plantilla con el historial
    }

}
