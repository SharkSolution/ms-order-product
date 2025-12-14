/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.suresell.order.controller.DiscountController
 *  com.suresell.order.exception.AdminPasswordException
 *  com.suresell.order.model.entity.DiscountCoupon
 *  com.suresell.order.model.record.AdminActionRequest
 *  com.suresell.order.model.record.ApplyDiscountCommand
 *  com.suresell.order.model.record.ApplyDiscountResult
 *  com.suresell.order.model.record.CreateCouponRequest
 *  com.suresell.order.model.record.LinkOrderCouponCommand
 *  com.suresell.order.model.record.UpdateCouponRequest
 *  com.suresell.order.serivices.DiscountService
 *  org.slf4j.Logger
 *  org.slf4j.LoggerFactory
 *  org.springframework.beans.factory.annotation.Autowired
 *  org.springframework.http.HttpStatus
 *  org.springframework.http.HttpStatusCode
 *  org.springframework.http.ResponseEntity
 *  org.springframework.web.bind.annotation.DeleteMapping
 *  org.springframework.web.bind.annotation.GetMapping
 *  org.springframework.web.bind.annotation.PatchMapping
 *  org.springframework.web.bind.annotation.PathVariable
 *  org.springframework.web.bind.annotation.PostMapping
 *  org.springframework.web.bind.annotation.PutMapping
 *  org.springframework.web.bind.annotation.RequestBody
 *  org.springframework.web.bind.annotation.RequestMapping
 *  org.springframework.web.bind.annotation.RequestParam
 *  org.springframework.web.bind.annotation.RestController
 */
package com.suresell.order.controller;
import com.suresell.order.exception.AdminPasswordException;
import com.suresell.order.model.entity.DiscountCoupon;
import com.suresell.order.model.record.AdminActionRequest;
import com.suresell.order.model.record.ApplyDiscountCommand;
import com.suresell.order.model.record.ApplyDiscountResult;
import com.suresell.order.model.record.CreateCouponRequest;
import com.suresell.order.model.record.LinkOrderCouponCommand;
import com.suresell.order.model.record.UpdateCouponRequest;
import com.suresell.order.serivices.DiscountService;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
@RestController
@RequestMapping(value={"/api/discounts"})
public class DiscountController {
    private static final Logger logger = LoggerFactory.getLogger(DiscountController.class);
    @Autowired
    private DiscountService discountService;
    @PostMapping(value={"/apply"})
    public ResponseEntity<ApplyDiscountResult> applyDiscount(@RequestBody ApplyDiscountCommand command) {
        logger.info("POST /api/discounts/apply - Aplicando cup\u00f3n: {}", (Object)command.code());
        try {
            ApplyDiscountResult result = this.discountService.applyDiscount(command);
            if (result.valid().booleanValue()) {
                logger.info("Cup\u00f3n aplicado exitosamente: {} - Descuento: ${}", (Object)command.code(), (Object)result.discountAmount());
            } else {
                logger.warn("Cup\u00f3n no v\u00e1lido: {} - Raz\u00f3n: {}", (Object)command.code(), (Object)result.message());
            }
            return ResponseEntity.ok(result);
        }
        catch (IllegalArgumentException e) {
            logger.warn("Datos inv\u00e1lidos para aplicar descuento: {}", (Object)e.getMessage());
            return ResponseEntity.badRequest().build();
        }
        catch (Exception e) {
            logger.error("Error inesperado aplicando descuento", (Throwable)e);
            return ResponseEntity.status((HttpStatusCode)HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    @PostMapping(value={"/link-order"})
    public ResponseEntity<Map<String, Object>> linkOrderWithCoupon(@RequestBody LinkOrderCouponCommand command) {
        logger.info("POST /api/discounts/link-order - Registrando cup\u00f3n {} para orden {}", (Object)command.code(), (Object)command.orderId());
        try {
            this.discountService.linkOrderWithCoupon(command);
            logger.info("Uso de cup\u00f3n registrado exitosamente para orden {}", (Object)command.orderId());
            return ResponseEntity.status((HttpStatusCode)HttpStatus.CREATED).body(Map.of("success", true, "message", "Cup\u00f3n registrado exitosamente", "orderId", command.orderId(), "discountCode", command.code()));
        }
        catch (IllegalArgumentException e) {
            logger.warn("Datos inv\u00e1lidos para registrar cup\u00f3n: {}", (Object)e.getMessage());
            return ResponseEntity.status((HttpStatusCode)HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        }
        catch (IllegalStateException e) {
            logger.warn("Estado inv\u00e1lido para registrar cup\u00f3n: {}", (Object)e.getMessage());
            return ResponseEntity.status((HttpStatusCode)HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        }
        catch (Exception e) {
            logger.error("Error inesperado registrando uso de cup\u00f3n", (Throwable)e);
            return ResponseEntity.status((HttpStatusCode)HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Error interno del servidor"));
        }
    }
    @GetMapping(value={"/active"})
    public ResponseEntity<?> getActiveCoupons() {
        logger.info("GET /api/discounts/active - Obteniendo cupones activos");
        try {
            List coupons = this.discountService.getActiveCoupons();
            return ResponseEntity.ok((Object)coupons);
        }
        catch (Exception e) {
            logger.error("Error obteniendo cupones activos", (Throwable)e);
            return ResponseEntity.status((HttpStatusCode)HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Error interno del servidor"));
        }
    }
    @PostMapping
    public ResponseEntity<?> createCoupon(@RequestBody CreateCouponRequest request) {
        logger.info("POST /api/discounts - Creando cup\u00f3n: {}", (Object)request.code());
        try {
            DiscountCoupon coupon = new DiscountCoupon();
            coupon.setCode(request.code());
            coupon.setName(request.name());
            coupon.setDescription(request.description());
            coupon.setDiscountPercentage(request.discountPercentage());
            coupon.setValidFrom(request.validFrom());
            coupon.setValidTo(request.validTo());
            coupon.setValidWeekdays(request.validWeekdays());
            coupon.setIsActive(Boolean.valueOf(request.isActive() != null ? request.isActive() : true));
            DiscountCoupon created = this.discountService.createCoupon(coupon, request.products());
            logger.info("Cup\u00f3n creado exitosamente: {}", (Object)created.getCode());
            return ResponseEntity.status((HttpStatusCode)HttpStatus.CREATED).body((Object)created);
        }
        catch (IllegalArgumentException e) {
            logger.warn("Datos inv\u00e1lidos para crear cup\u00f3n: {}", (Object)e.getMessage());
            return ResponseEntity.status((HttpStatusCode)HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        }
        catch (Exception e) {
            logger.error("Error inesperado creando cup\u00f3n", (Throwable)e);
            return ResponseEntity.status((HttpStatusCode)HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Error interno del servidor"));
        }
    }
    @PutMapping(value={"/{id}"})
    public ResponseEntity<?> updateCoupon(@PathVariable Long id, @RequestBody UpdateCouponRequest request) {
        logger.info("PUT /api/discounts/{} - Actualizando cup\u00f3n", (Object)id);
        try {
            DiscountCoupon updatedData = new DiscountCoupon();
            updatedData.setCode(request.code());
            updatedData.setName(request.name());
            updatedData.setDescription(request.description());
            updatedData.setDiscountPercentage(request.discountPercentage());
            updatedData.setValidFrom(request.validFrom());
            updatedData.setValidTo(request.validTo());
            updatedData.setValidWeekdays(request.validWeekdays());
            updatedData.setIsActive(request.isActive());
            DiscountCoupon updated = this.discountService.updateCoupon(id, updatedData, request.products());
            logger.info("Cup\u00f3n actualizado exitosamente: {}", (Object)updated.getCode());
            return ResponseEntity.ok((Object)updated);
        }
        catch (IllegalArgumentException e) {
            logger.warn("Datos inv\u00e1lidos para actualizar cup\u00f3n: {}", (Object)e.getMessage());
            return ResponseEntity.status((HttpStatusCode)HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        }
        catch (Exception e) {
            logger.error("Error inesperado actualizando cup\u00f3n", (Throwable)e);
            return ResponseEntity.status((HttpStatusCode)HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Error interno del servidor"));
        }
    }
    @PatchMapping(value={"/{id}/deactivate"})
    public ResponseEntity<?> deactivateCoupon(@PathVariable Long id) {
        logger.info("PATCH /api/discounts/{}/deactivate - Desactivando cup\u00f3n", (Object)id);
        try {
            DiscountCoupon deactivated = this.discountService.deactivateCoupon(id);
            logger.info("Cup\u00f3n desactivado exitosamente: {}", (Object)deactivated.getCode());
            return ResponseEntity.ok().body(Map.of("success", true, "message", "Cup\u00f3n desactivado exitosamente", "coupon", deactivated));
        }
        catch (IllegalArgumentException e) {
            logger.warn("Error desactivando cup\u00f3n: {}", (Object)e.getMessage());
            return ResponseEntity.status((HttpStatusCode)HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        }
        catch (Exception e) {
            logger.error("Error inesperado desactivando cup\u00f3n", (Throwable)e);
            return ResponseEntity.status((HttpStatusCode)HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Error interno del servidor"));
        }
    }
    @GetMapping
    public ResponseEntity<?> listAllCoupons(@RequestParam(defaultValue="all") String status) {
        logger.info("GET /api/discounts - Listando cupones con filtro: {}", (Object)status);
        try {
            List coupons = this.discountService.listAllCoupons(status);
            return ResponseEntity.ok((Object)coupons);
        }
        catch (Exception e) {
            logger.error("Error listando cupones", (Throwable)e);
            return ResponseEntity.status((HttpStatusCode)HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Error interno del servidor"));
        }
    }
    @DeleteMapping(value={"/{id}"})
    public ResponseEntity<?> deleteCoupon(@PathVariable Long id) {
        logger.info("DELETE /api/discounts/{} - Eliminando cup\u00f3n", (Object)id);
        try {
            this.discountService.deleteCoupon(id);
            logger.info("Cup\u00f3n ID {} eliminado exitosamente", (Object)id);
            return ResponseEntity.ok().body(Map.of("success", true, "message", "Cup\u00f3n eliminado exitosamente", "couponId", id));
        }
        catch (IllegalArgumentException e) {
            logger.warn("Error eliminando cup\u00f3n: {}", (Object)e.getMessage());
            return ResponseEntity.status((HttpStatusCode)HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        }
        catch (IllegalStateException e) {
            logger.warn("No se puede eliminar el cup\u00f3n: {}", (Object)e.getMessage());
            return ResponseEntity.status((HttpStatusCode)HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        }
        catch (Exception e) {
            logger.error("Error inesperado eliminando cup\u00f3n", (Throwable)e);
            return ResponseEntity.status((HttpStatusCode)HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Error interno del servidor"));
        }
    }
}
