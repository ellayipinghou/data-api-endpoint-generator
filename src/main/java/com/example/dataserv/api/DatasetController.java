package com.example.dataserv.api;

import com.example.dataserv.application.DatasetService;
import com.example.dataserv.domain.DataRow;
import com.example.dataserv.domain.DataType;
import com.example.dataserv.domain.Dataset;
import com.example.dataserv.domain.DatasetSchema;
import com.example.dataserv.domain.Filter;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import java.util.Map;

@CrossOrigin(origins = {"http://localhost:5173", "http://localhost:3000"})
@RestController
@RequestMapping("/datasets")
public class DatasetController {
    private final DatasetService service;

    public DatasetController(DatasetService service) {
        this.service = service;
    }

    /*
     * =========================
     * Dataset creation
     * =========================
     */

    // TODO: is RequestPart necessary? could just make RequestParam if not planning on adding json to uploads
    @PostMapping("/preview")
    public ResponseEntity<com.example.dataserv.api.DatasetPreviewResponse> previewDataset(@RequestPart MultipartFile file) throws IOException {
        com.example.dataserv.api.DatasetPreviewResponse preview = service.previewDataset(file);
        return ResponseEntity.ok(preview);
    }

    @PostMapping
    public ResponseEntity<Dataset> createDataset(@RequestParam String name, @RequestPart DatasetSchema schema, @RequestPart MultipartFile file) throws IOException, SQLException {
        Dataset dataset = service.createDataset(name, schema, file.getInputStream());
        return ResponseEntity.ok(dataset);
    }

    public record CreateFromPreviewRequest(String name, UUID previewId, Map<String, DataType> typeOverrides) {}
    @PostMapping("/from-preview")
    public ResponseEntity<Dataset> createDatasetFromPreview(@RequestBody CreateFromPreviewRequest request) throws IOException, SQLException {
        Dataset dataset = service.createDatasetFromPreview(
            request.name(), request.previewId(), request.typeOverrides()
        );
        return ResponseEntity.ok(dataset);
    }

    /*
     * =========================
     * Dataset retrieval
     * =========================
     */

    @GetMapping
    public ResponseEntity<List<Dataset>> getDatasets() {
        return ResponseEntity.ok(service.findAllDatasets());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Dataset> getDataset(@PathVariable UUID id) {
        return service.findDataset(id)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /*
     * =========================
     * Dataset deletion
     * =========================
     */

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDataset(@PathVariable UUID id) {
        service.deleteDataset(id);
        return ResponseEntity.noContent().build();
    }

    /*
    * =========================
    * Dataset querying
    * =========================
    */

    @PostMapping("/{id}/query")
    public ResponseEntity<List<DataRow>> queryDataset(@PathVariable UUID id,@RequestBody List<Filter> filters) {
        return ResponseEntity.ok(service.queryDataset(id, filters));
    }

    @GetMapping("/{id}/query")
    public ResponseEntity<List<DataRow>> getDatasetQuery(@PathVariable UUID id, @RequestParam(required = false) java.util.Map<String, String> params) {
        if (params == null) {
                params = java.util.Map.of();
        }

        return ResponseEntity.ok(service.queryDatasetWithParams(id, params));
    }
}