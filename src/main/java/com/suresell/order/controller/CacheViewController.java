package com.suresell.order.controller;

import com.suresell.order.serivices.DiskCacheService;
import com.suresell.order.serivices.ResilientOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Controller para visualizar el contenido del cache desde el navegador.
 * Útil para ver qué hay en cache sin acceder a los archivos directamente.
 */
@RestController
@RequestMapping("/api/cache")
@RequiredArgsConstructor
public class CacheViewController {

    private final DiskCacheService diskCacheService;
    private final ResilientOrderService resilientOrderService;

    /**
     * Lista todos los archivos en cache
     * GET http://localhost:8081/api/cache/files
     */
    @GetMapping("/files")
    public ResponseEntity<Map<String, Object>> listCacheFiles() {
        try {
            var stats = diskCacheService.getStats();

            Path cachePath = Paths.get(stats.cachePath());

            List<Map<String, Object>> files = Files.walk(cachePath)
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".json"))
                    .map(path -> {
                        Map<String, Object> fileInfo = new HashMap<>();
                        try {
                            fileInfo.put("name", path.getFileName().toString());
                            fileInfo.put("path", path.toString());
                            fileInfo.put("size", Files.size(path) + " bytes");
                            fileInfo.put("lastModified", Files.getLastModifiedTime(path).toString());
                        } catch (IOException e) {
                            fileInfo.put("error", e.getMessage());
                        }
                        return fileInfo;
                    })
                    .collect(Collectors.toList());

            Map<String, Object> response = new HashMap<>();
            response.put("cacheEnabled", stats.enabled());
            response.put("cachePath", stats.cachePath());
            response.put("totalFiles", stats.totalFiles());
            response.put("totalSize", stats.formatSize());
            response.put("files", files);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Failed to list cache files: " + e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }

    /**
     * Ver órdenes de cocina en cache
     * GET http://localhost:8081/api/cache/kitchen-orders
     */
    @GetMapping("/kitchen-orders")
    public ResponseEntity<Object> viewKitchenOrders() {
        try {
            var orders = resilientOrderService.getKitchenOrders();

            Map<String, Object> response = new HashMap<>();
            response.put("source", "cache");
            response.put("count", orders.size());
            response.put("orders", orders);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Failed to read kitchen orders: " + e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }

    /**
     * Ver órdenes offline pendientes de sincronización
     * GET http://localhost:8081/api/cache/offline-orders
     */
    @GetMapping("/offline-orders")
    public ResponseEntity<Object> viewOfflineOrders() {
        try {
            var offlineOrders = resilientOrderService.getOfflineOrdersIndex();

            Map<String, Object> response = new HashMap<>();
            response.put("count", offlineOrders.size());
            response.put("pendingSync", offlineOrders.stream().filter(o -> !o.synced()).count());
            response.put("synced", offlineOrders.stream().filter(o -> o.synced()).count());
            response.put("orders", offlineOrders);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Failed to read offline orders: " + e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }







    /**
     * Dashboard de cache (HTML visual y bonito)
     * GET http://localhost:8081/api/cache/dashboard
     */
    @GetMapping(value = "/dashboard", produces = "text/html")
    public ResponseEntity<String> cacheDashboard() {
        String html = getModernDashboardHTML();
        return ResponseEntity.ok().header("Cache-Control", "no-cache, no-store, must-revalidate").body(html);
    }

    private String getModernDashboardHTML() {
        return """
            <!DOCTYPE html>
            <html lang="es">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Dashboard de Caché - SureSell</title>
                <style>
                    * {
                        margin: 0;
                        padding: 0;
                        box-sizing: border-box;
                    }

                    body {
                        font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
                        background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
                        min-height: 100vh;
                        padding: 20px;
                    }

                    .container {
                        max-width: 1400px;
                        margin: 0 auto;
                    }

                    .header {
                        background: white;
                        border-radius: 15px;
                        padding: 30px;
                        margin-bottom: 20px;
                        box-shadow: 0 10px 30px rgba(0,0,0,0.2);
                    }

                    .header h1 {
                        color: #333;
                        font-size: 32px;
                        margin-bottom: 10px;
                    }

                    .header p {
                        color: #666;
                        font-size: 16px;
                    }

                    .refresh-btn {
                        float: right;
                        background: #667eea;
                        color: white;
                        border: none;
                        padding: 12px 25px;
                        border-radius: 8px;
                        cursor: pointer;
                        font-size: 16px;
                        font-weight: bold;
                        transition: all 0.3s;
                    }

                    .refresh-btn:hover {
                        background: #5568d3;
                        transform: translateY(-2px);
                        box-shadow: 0 5px 15px rgba(102, 126, 234, 0.4);
                    }

                    .stats-grid {
                        display: grid;
                        grid-template-columns: repeat(auto-fit, minmax(250px, 1fr));
                        gap: 20px;
                        margin-bottom: 20px;
                    }

                    .stat-card {
                        background: white;
                        border-radius: 15px;
                        padding: 25px;
                        box-shadow: 0 10px 30px rgba(0,0,0,0.2);
                        transition: transform 0.3s;
                    }

                    .stat-card:hover {
                        transform: translateY(-5px);
                    }

                    .stat-card h3 {
                        color: #666;
                        font-size: 14px;
                        text-transform: uppercase;
                        margin-bottom: 10px;
                        font-weight: 600;
                    }

                    .stat-card .value {
                        font-size: 36px;
                        font-weight: bold;
                        margin-bottom: 5px;
                    }

                    .stat-card .label {
                        color: #999;
                        font-size: 13px;
                    }

                    .stat-card.blue .value { color: #667eea; }
                    .stat-card.green .value { color: #28a745; }
                    .stat-card.orange .value { color: #ff9800; }
                    .stat-card.red .value { color: #f44336; }

                    .content-grid {
                        display: grid;
                        grid-template-columns: 1fr;
                        gap: 20px;
                    }

                    .card {
                        background: white;
                        border-radius: 15px;
                        padding: 25px;
                        box-shadow: 0 10px 30px rgba(0,0,0,0.2);
                    }

                    .card h2 {
                        color: #333;
                        font-size: 24px;
                        margin-bottom: 20px;
                        padding-bottom: 15px;
                        border-bottom: 3px solid #667eea;
                    }

                    table {
                        width: 100%;
                        border-collapse: collapse;
                        margin-top: 15px;
                    }

                    th {
                        background: #f8f9fa;
                        padding: 15px;
                        text-align: left;
                        font-weight: 600;
                        color: #333;
                        border-bottom: 2px solid #dee2e6;
                    }

                    td {
                        padding: 15px;
                        border-bottom: 1px solid #dee2e6;
                        color: #555;
                    }

                    tr:hover {
                        background: #f8f9fa;
                    }

                    .badge {
                        display: inline-block;
                        padding: 5px 12px;
                        border-radius: 20px;
                        font-size: 12px;
                        font-weight: bold;
                        text-transform: uppercase;
                    }

                    .badge.success {
                        background: #d4edda;
                        color: #155724;
                    }

                    .badge.warning {
                        background: #fff3cd;
                        color: #856404;
                    }

                    .badge.danger {
                        background: #f8d7da;
                        color: #721c24;
                    }

                    .badge.info {
                        background: #d1ecf1;
                        color: #0c5460;
                    }

                    .btn {
                        padding: 12px 24px;
                        border: none;
                        border-radius: 8px;
                        cursor: pointer;
                        font-size: 14px;
                        font-weight: 600;
                        margin-right: 10px;
                        margin-bottom: 10px;
                        transition: all 0.3s;
                        color: white;
                    }

                    .btn:hover {
                        transform: translateY(-2px);
                        box-shadow: 0 5px 15px rgba(0,0,0,0.3);
                    }

                    .btn-success {
                        background: #28a745;
                    }

                    .btn-warning {
                        background: #ff9800;
                    }

                    .btn-danger {
                        background: #f44336;
                    }

                    .empty-state {
                        text-align: center;
                        padding: 60px 20px;
                        color: #999;
                    }

                    .empty-state .icon {
                        font-size: 64px;
                        margin-bottom: 20px;
                    }

                    .loading {
                        text-align: center;
                        padding: 40px;
                        color: #667eea;
                        font-size: 18px;
                    }

                    .spinner {
                        border: 4px solid #f3f3f3;
                        border-top: 4px solid #667eea;
                        border-radius: 50%;
                        width: 40px;
                        height: 40px;
                        animation: spin 1s linear infinite;
                        margin: 0 auto 20px;
                    }

                    @keyframes spin {
                        0% { transform: rotate(0deg); }
                        100% { transform: rotate(360deg); }
                    }

                    .alert {
                        padding: 15px 20px;
                        border-radius: 8px;
                        margin-bottom: 20px;
                    }

                    .alert-success {
                        background: #d4edda;
                        color: #155724;
                        border-left: 4px solid #28a745;
                    }

                    .alert-warning {
                        background: #fff3cd;
                        color: #856404;
                        border-left: 4px solid #ffc107;
                    }

                    .timestamp {
                        font-size: 12px;
                        color: #999;
                    }

                    .btn-view {
                        background: #667eea;
                        color: white;
                        border: none;
                        padding: 8px 16px;
                        border-radius: 6px;
                        cursor: pointer;
                        font-size: 13px;
                        font-weight: 600;
                        transition: all 0.3s;
                    }

                    .btn-view:hover {
                        background: #5568d3;
                        transform: scale(1.05);
                    }

                    /* Modal Styles */
                    .modal {
                        display: none;
                        position: fixed;
                        z-index: 1000;
                        left: 0;
                        top: 0;
                        width: 100%;
                        height: 100%;
                        background: rgba(0, 0, 0, 0.7);
                        backdrop-filter: blur(5px);
                        animation: fadeIn 0.3s;
                    }

                    @keyframes fadeIn {
                        from { opacity: 0; }
                        to { opacity: 1; }
                    }

                    .modal-content {
                        background: white;
                        margin: 5% auto;
                        padding: 0;
                        border-radius: 20px;
                        width: 90%;
                        max-width: 600px;
                        box-shadow: 0 20px 60px rgba(0,0,0,0.3);
                        animation: slideIn 0.3s;
                        max-height: 85vh;
                        overflow-y: auto;
                    }

                    @keyframes slideIn {
                        from {
                            transform: translateY(-50px);
                            opacity: 0;
                        }
                        to {
                            transform: translateY(0);
                            opacity: 1;
                        }
                    }

                    .modal-header {
                        background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
                        color: white;
                        padding: 30px;
                        border-radius: 20px 20px 0 0;
                        position: relative;
                    }

                    .modal-header h2 {
                        color: white;
                        border: none;
                        padding: 0;
                        margin: 0;
                        font-size: 28px;
                    }

                    .modal-header .subtitle {
                        font-size: 14px;
                        opacity: 0.9;
                        margin-top: 5px;
                    }

                    .close {
                        position: absolute;
                        right: 20px;
                        top: 20px;
                        color: white;
                        font-size: 35px;
                        font-weight: bold;
                        cursor: pointer;
                        width: 40px;
                        height: 40px;
                        display: flex;
                        align-items: center;
                        justify-content: center;
                        border-radius: 50%;
                        transition: all 0.3s;
                    }

                    .close:hover {
                        background: rgba(255,255,255,0.2);
                        transform: rotate(90deg);
                    }

                    .modal-body {
                        padding: 30px;
                    }

                    .detail-section {
                        margin-bottom: 25px;
                    }

                    .detail-section h3 {
                        color: #667eea;
                        font-size: 18px;
                        margin-bottom: 15px;
                        padding-bottom: 10px;
                        border-bottom: 2px solid #f0f0f0;
                    }

                    .detail-row {
                        display: flex;
                        justify-content: space-between;
                        padding: 12px 0;
                        border-bottom: 1px solid #f5f5f5;
                    }

                    .detail-row:last-child {
                        border-bottom: none;
                    }

                    .detail-label {
                        font-weight: 600;
                        color: #666;
                    }

                    .detail-value {
                        color: #333;
                        font-weight: 500;
                    }

                    .product-item {
                        background: #f8f9fa;
                        padding: 15px;
                        border-radius: 10px;
                        margin-bottom: 10px;
                        border-left: 4px solid #667eea;
                    }

                    .product-item .product-name {
                        font-weight: bold;
                        font-size: 16px;
                        color: #333;
                        margin-bottom: 5px;
                    }

                    .product-item .product-details {
                        display: flex;
                        justify-content: space-between;
                        color: #666;
                        font-size: 14px;
                    }

                    .product-item .product-instructions {
                        margin-top: 8px;
                        padding-top: 8px;
                        border-top: 1px dashed #ddd;
                        font-style: italic;
                        color: #666;
                        font-size: 13px;
                    }

                    .total-section {
                        background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
                        color: white;
                        padding: 20px;
                        border-radius: 10px;
                        margin-top: 20px;
                    }

                    .total-section .total-row {
                        display: flex;
                        justify-content: space-between;
                        margin-bottom: 10px;
                    }

                    .total-section .total-row:last-child {
                        margin-bottom: 0;
                        padding-top: 10px;
                        border-top: 2px solid rgba(255,255,255,0.3);
                        font-size: 20px;
                        font-weight: bold;
                    }

                    .status-badge-large {
                        display: inline-block;
                        padding: 8px 16px;
                        border-radius: 20px;
                        font-size: 14px;
                        font-weight: bold;
                    }

                    .pager-badge {
                        display: inline-block;
                        padding: 10px 20px;
                        border-radius: 10px;
                        font-size: 18px;
                        font-weight: bold;
                        background: #667eea;
                        color: white;
                    }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">
                        <button class="refresh-btn" onclick="loadData()">🔄 Actualizar</button>
                        <h1>📊 Dashboard de Caché</h1>
                        <p>Visualización en tiempo real del sistema de caché local</p>
                    </div>

                    <div class="stats-grid" id="stats">
                        <div class="loading">
                            <div class="spinner"></div>
                            Cargando estadísticas...
                        </div>
                    </div>

                    <div class="content-grid">
                        <div class="card">
                            <h2>📦 Órdenes Offline</h2>
                            <div id="offline-orders">
                                <div class="loading">
                                    <div class="spinner"></div>
                                    Cargando órdenes offline...
                                </div>
                            </div>
                        </div>

                        <div class="card">
                            <h2>🍳 Órdenes de Cocina</h2>
                            <div id="kitchen-orders">
                                <div class="loading">
                                    <div class="spinner"></div>
                                    Cargando órdenes de cocina...
                                </div>
                            </div>
                        </div>


                    </div>
                </div>

                <!-- Modal para ver detalles de orden -->
                <div id="orderModal" class="modal">
                    <div class="modal-content">
                        <div class="modal-header">
                            <span class="close" onclick="closeModal()">&times;</span>
                            <h2 id="modalTitle">Detalles de la Orden</h2>
                            <div class="subtitle" id="modalSubtitle"></div>
                        </div>
                        <div class="modal-body" id="modalBody">
                            <!-- Contenido dinámico -->
                        </div>
                    </div>
                </div>

                <script>
                    function formatCurrency(valueInCents) {
                        const pesos = valueInCents / 100;
                        return '$' + pesos.toLocaleString('es-CO');
                    }

                    function loadData() {
                        loadStats();
                        loadOfflineOrders();
                        loadKitchenOrders();
                    }

                    function loadStats() {
                        Promise.all([
                            fetch('/api/cache/files').then(r => r.json()),
                            fetch('/api/cache/offline-orders').then(r => r.json())
                        ])
                        .then(([files, offline]) => {
                            const html = `
                                <div class="stat-card blue">
                                    <h3>Estado del Cache</h3>
                                    <div class="value">${files.cacheEnabled ? '✅' : '❌'}</div>
                                    <div class="label">${files.cacheEnabled ? 'Activo' : 'Desactivado'}</div>
                                </div>
                                <div class="stat-card green">
                                    <h3>Total de Archivos</h3>
                                    <div class="value">${files.totalFiles}</div>
                                    <div class="label">archivos en disco</div>
                                </div>
                                <div class="stat-card orange">
                                    <h3>Pendientes Sync</h3>
                                    <div class="value">${offline.pendingSync}</div>
                                    <div class="label">órdenes sin sincronizar</div>
                                </div>
                                <div class="stat-card blue">
                                    <h3>Tamaño Total</h3>
                                    <div class="value">${files.totalSize}</div>
                                    <div class="label">espacio usado</div>
                                </div>
                            `;
                            document.getElementById('stats').innerHTML = html;
                        })
                        .catch(err => {
                            document.getElementById('stats').innerHTML =
                                '<div class="alert alert-warning">❌ Error al cargar estadísticas</div>';
                        });
                    }

                    function loadOfflineOrders() {
                        fetch('/api/cache/offline-orders')
                            .then(r => r.json())
                            .then(data => {
                                if (data.orders.length === 0) {
                                    document.getElementById('offline-orders').innerHTML = `
                                        <div class="empty-state">
                                            <div class="icon">📭</div>
                                            <h3>No hay órdenes offline</h3>
                                            <p>Todas las órdenes están sincronizadas con AWS</p>
                                        </div>
                                    `;
                                    return;
                                }

                                let html = '<table><thead><tr>';
                                html += '<th>ID Local</th>';
                                html += '<th>ID AWS</th>';
                                html += '<th>Creada</th>';
                                html += '<th>Productos</th>';
                                html += '<th>Total</th>';
                                html += '<th>Estado</th>';
                                html += '<th>Intentos</th>';
                                html += '<th>Acciones</th>';
                                html += '</tr></thead><tbody>';

                                data.orders.forEach((order, index) => {
                                    const itemCount = order.orderData.items.length;
                                    const total = order.orderData.items.reduce((sum, item) =>
                                        sum + (item.quantity * item.unitPrice), 0);
                                    const status = order.synced ?
                                        '<span class="badge success">✅ Sincronizada</span>' :
                                        '<span class="badge warning">⏳ Pendiente</span>';

                                    html += `<tr>
                                        <td><small>${order.localOrderId.substring(6, 16)}...</small></td>
                                        <td>${order.externalOrderId || 'N/A'}</td>
                                        <td><span class="timestamp">${formatDate(order.createdAt)}</span></td>
                                        <td>${itemCount} item(s)</td>
                                        <td>${formatCurrency(total)}</td>
                                        <td>${status}</td>
                                        <td>${order.syncAttempts}</td>
                                        <td>
                                            <button class="btn-view" onclick='viewOfflineOrder(${JSON.stringify(order)})'>
                                                👁️ Ver
                                            </button>
                                        </td>
                                    </tr>`;
                                });

                                html += '</tbody></table>';
                                document.getElementById('offline-orders').innerHTML = html;
                            })
                            .catch(err => {
                                document.getElementById('offline-orders').innerHTML =
                                    '<div class="alert alert-warning">❌ Error al cargar órdenes offline</div>';
                            });
                    }

                    function loadKitchenOrders() {
                        fetch('/api/cache/kitchen-orders')
                            .then(r => r.json())
                            .then(data => {
                                if (data.orders.length === 0) {
                                    document.getElementById('kitchen-orders').innerHTML = `
                                        <div class="empty-state">
                                            <div class="icon">🍽️</div>
                                            <h3>No hay órdenes en cocina</h3>
                                            <p>No hay pedidos activos en este momento</p>
                                        </div>
                                    `;
                                    return;
                                }

                                let html = '<table><thead><tr>';
                                html += '<th>Pager</th>';
                                html += '<th>Productos</th>';
                                html += '<th>Total</th>';
                                html += '<th>Estado</th>';
                                html += '<th>Hora</th>';
                                html += '<th>Acciones</th>';
                                html += '</tr></thead><tbody>';

                                data.orders.forEach((order, index) => {
                                    const itemsText = order.items.map(item =>
                                        `${item.quantity}x ${item.nameProduct}`
                                    ).join(', ');

                                    html += `<tr>
                                        <td><strong>${order.pagerColor} #${order.pagerNumber}</strong></td>
                                        <td>${itemsText}</td>
                                        <td>${formatCurrency(order.total)}</td>
                                        <td><span class="badge info">${order.status}</span></td>
                                        <td><span class="timestamp">${formatDate(order.createdAt)}</span></td>
                                        <td>
                                            <button class="btn-view" onclick='viewKitchenOrder(${JSON.stringify(order)})'>
                                                👁️ Ver
                                            </button>
                                        </td>
                                    </tr>`;
                                });

                                html += '</tbody></table>';
                                document.getElementById('kitchen-orders').innerHTML = html;
                            })
                            .catch(err => {
                                document.getElementById('kitchen-orders').innerHTML =
                                    '<div class="alert alert-warning">❌ Error al cargar órdenes de cocina</div>';
                            });
                    }

                    function formatDate(dateStr) {
                        const date = new Date(dateStr);
                        return date.toLocaleString('es-CO', {
                            day: '2-digit',
                            month: '2-digit',
                            year: 'numeric',
                            hour: '2-digit',
                            minute: '2-digit'
                        });
                    }



                    function viewOfflineOrder(order) {
                        const total = order.orderData.items.reduce((sum, item) =>
                            sum + (item.quantity * item.unitPrice), 0);

                        document.getElementById('modalTitle').textContent =
                            `Orden Offline - ${order.localOrderId.substring(6, 20)}...`;
                        document.getElementById('modalSubtitle').textContent =
                            `Creada: ${formatDate(order.createdAt)}`;

                        let html = '<div class="detail-section">';
                        html += '<h3>📋 Información General</h3>';
                        if (order.externalOrderId) {
                            html += `<div class="detail-row">
                                <span class="detail-label">ID Externo (AWS):</span>
                                <span class="detail-value">${order.externalOrderId}</span>
                            </div>`;
                        }
                        html += `<div class="detail-row">
                            <span class="detail-label">Pager:</span>
                            <span class="detail-value"><span class="pager-badge">${order.orderData.pagerColor} #${order.orderData.pagerNumber}</span></span>
                        </div>`;
                        html += `<div class="detail-row">
                            <span class="detail-label">Método de Pago:</span>
                            <span class="detail-value">${order.orderData.paymentMethod}</span>
                        </div>`;
                        html += `<div class="detail-row">
                            <span class="detail-label">Estado de Sync:</span>
                            <span class="detail-value">${order.synced ?
                                '<span class="status-badge-large badge success">✅ Sincronizada</span>' :
                                '<span class="status-badge-large badge warning">⏳ Pendiente de Sincronización</span>'}</span>
                        </div>`;
                        html += `<div class="detail-row">
                            <span class="detail-label">Intentos de Sync:</span>
                            <span class="detail-value">${order.syncAttempts}</span>
                        </div>`;
                        if (order.lastError) {
                            html += `<div class="detail-row">
                                <span class="detail-label">Último Error:</span>
                                <span class="detail-value" style="color: #f44336;">${order.lastError}</span>
                            </div>`;
                        }
                        html += '</div>';

                        html += '<div class="detail-section">';
                        html += '<h3>🍔 Productos</h3>';
                        order.orderData.items.forEach(item => {
                            html += `<div class="product-item">
                                <div class="product-name">${item.quantity}x Producto ID: ${item.productId}</div>
                                <div class="product-details">
                                    <span>Precio Unitario: ${item.unitPrice}</span>
                                    <span>Subtotal: ${item.quantity * item.unitPrice}</span>
                                </div>
                                ${item.instructions ? `<div class="product-instructions">📝 ${item.instructions}</div>` : ''}
                            </div>`;
                        });
                        html += '</div>';

                        html += '<div class="total-section">';
                        html += '<div class="total-row"><span>Total de la Orden:</span><span>' + total + '</span></div>';
                        html += '</div>';

                        document.getElementById('modalBody').innerHTML = html;
                        document.getElementById('orderModal').style.display = 'block';
                    }

                    function viewKitchenOrder(order) {
                        document.getElementById('modalTitle').textContent =
                            `Orden de Cocina ${order.idOrder ? '#' + order.idOrder : ''}`;
                        document.getElementById('modalSubtitle').textContent =
                            `Creada: ${formatDate(order.createdAt)}`;

                        let html = '<div class="detail-section">';
                        html += '<h3>📋 Información General</h3>';
                        html += `<div class="detail-row">
                            <span class="detail-label">Pager:</span>
                            <span class="detail-value"><span class="pager-badge">${order.pagerColor} #${order.pagerNumber}</span></span>
                        </div>`;
                        html += `<div class="detail-row">
                            <span class="detail-label">ID de Orden:</span>
                            <span class="detail-value">${order.idOrder || 'Sin ID'}</span>
                        </div>`;
                        html += `<div class="detail-row">
                            <span class="detail-label">Estado:</span>
                            <span class="detail-value"><span class="status-badge-large badge info">${order.status}</span></span>
                        </div>`;
                        html += `<div class="detail-row">
                            <span class="detail-label">Método de Pago:</span>
                            <span class="detail-value">${order.paymentMethod}</span>
                        </div>`;
                        if (order.discountCode) {
                            html += `<div class="detail-row">
                                <span class="detail-label">Cupón de Descuento:</span>
                                <span class="detail-value">${order.discountCode} (-${order.discountAmount})</span>
                            </div>`;
                        }
                        html += '</div>';

                        html += '<div class="detail-section">';
                        html += '<h3>🍔 Productos</h3>';
                        order.items.forEach(item => {
                            html += `<div class="product-item">
                                <div class="product-name">${item.quantity}x ${item.nameProduct}</div>
                                <div class="product-details">
                                    <span>Precio Unitario: ${item.unitPrice}</span>
                                    <span>Subtotal: ${item.totalPrice}</span>
                                </div>
                                ${item.instructions ? `<div class="product-instructions">📝 ${item.instructions}</div>` : ''}
                            </div>`;
                        });
                        html += '</div>';

                        html += '<div class="total-section">';
                        html += `<div class="total-row"><span>Subtotal:</span><span>${order.subtotal}</span></div>`;
                        if (order.discountAmount) {
                            html += `<div class="total-row"><span>Descuento:</span><span>-${order.discountAmount}</span></div>`;
                        }
                        html += `<div class="total-row"><span>Total:</span><span>${order.total}</span></div>`;
                        html += '</div>';

                        document.getElementById('modalBody').innerHTML = html;
                        document.getElementById('orderModal').style.display = 'block';
                    }

                    function closeModal() {
                        document.getElementById('orderModal').style.display = 'none';
                    }

                    // Cerrar modal al hacer clic fuera de él
                    window.onclick = function(event) {
                        const modal = document.getElementById('orderModal');
                        if (event.target == modal) {
                            closeModal();
                        }
                    }

                    // Cerrar modal con tecla ESC
                    document.addEventListener('keydown', function(event) {
                        if (event.key === 'Escape') {
                            closeModal();
                        }
                    });

                    // Cargar datos al inicio
                    loadData();

                    // Actualizar cada 30 segundos
                    setInterval(loadData, 30000);
                </script>
            </body>
            </html>
            """;
    }
}
