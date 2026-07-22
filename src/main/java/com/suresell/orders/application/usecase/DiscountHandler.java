package com.suresell.orders.application.usecase;
import com.suresell.orders.domain.port.in.DiscountPort;
import com.suresell.orders.domain.model.CouponProduct;
import com.suresell.orders.domain.model.DiscountCoupon;
import com.suresell.orders.domain.model.DiscountUsage;
import com.suresell.orders.application.dto.ApplyDiscountCommand;
import com.suresell.orders.application.dto.ApplyDiscountResult;
import com.suresell.orders.application.dto.LinkOrderCouponCommand;
import com.suresell.orders.application.dto.OrderItemDto;
import com.suresell.orders.application.dto.ProductDiscountDto;
import com.suresell.orders.domain.port.out.CouponProductRepositoryPort;
import com.suresell.orders.domain.port.out.DiscountCouponRepositoryPort;
import com.suresell.orders.domain.port.out.DiscountUsageRepositoryPort;
import com.suresell.orders.domain.port.out.SyncOutboxRepositoryPort;
import com.suresell.orders.domain.model.SyncOutbox;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class DiscountHandler implements DiscountPort {
    private static final Logger logger = LoggerFactory.getLogger(DiscountHandler.class);
    @Autowired
    private DiscountCouponRepositoryPort couponRepositoryPort;
    @Autowired
    private DiscountUsageRepositoryPort usageRepositoryPort;
    @Autowired
    private CouponProductRepositoryPort couponProductRepositoryPort;
    @Autowired
    private SyncOutboxRepositoryPort syncOutboxRepositoryPort;
    @Autowired
    private ObjectMapper objectMapper;
    private static final ZoneId BOGOTA_ZONE = ZoneId.of("America/Bogota");
    @Override
    public ApplyDiscountResult applyDiscount(ApplyDiscountCommand command) {
        LocalDate orderDate;
        if (command.code() == null || command.code().trim().isEmpty()) {
            return this.createInvalidResult("El código del cupón es requerido");
        }
        if (command.subtotal() == null || command.subtotal().compareTo(BigDecimal.ZERO) <= 0) {
            return this.createInvalidResult("El subtotal debe ser mayor a cero");
        }
        if (command.items() == null || command.items().isEmpty()) {
            return this.createInvalidResult("La orden debe tener al menos un producto");
        }
        Optional<DiscountCoupon> couponOpt = this.couponRepositoryPort.findByCodeIgnoreCase(command.code());
        if (couponOpt.isEmpty()) {
            return this.createInvalidResult("El cupón no existe");
        }
        DiscountCoupon coupon = couponOpt.get();
        if (!Boolean.TRUE.equals(coupon.getIsActive())) {
            return this.createInvalidResult("El cupón no está activo");
        }
        orderDate = command.orderDateTime() != null ? command.orderDateTime().toLocalDate() : LocalDate.now(BOGOTA_ZONE);
        if (coupon.getValidFrom() != null && orderDate.isBefore(coupon.getValidFrom())) {
            return this.createInvalidResult("El cupón aún no es válido. Válido desde: " + coupon.getValidFrom());
        }
        if (coupon.getValidTo() != null && orderDate.isAfter(coupon.getValidTo())) {
            return this.createInvalidResult("El cupón ha expirado. Válido hasta: " + coupon.getValidTo());
        }
        if (coupon.getValidWeekdays() != null && !coupon.getValidWeekdays().trim().isEmpty()) {
            DayOfWeek currentDay = command.orderDateTime() != null ? command.orderDateTime().getDayOfWeek() : LocalDateTime.now(BOGOTA_ZONE).getDayOfWeek();
            List<String> validDays = Arrays.stream(coupon.getValidWeekdays().split(",")).map(String::trim).map(String::toUpperCase).collect(Collectors.toList());
            String currentDayStr = currentDay.toString().substring(0, 3);
            if (!validDays.contains(currentDayStr)) {
                return this.createInvalidResult("El cupón no es válido para " + currentDay.toString());
            }
        }
        // Cupón SIN productos asociados = descuento GENERAL: aplica a toda la orden.
        // (Bug "cupones a medias": el admin permite crear cupones sin seleccionar
        // productos, pero aquí se rechazaban con "no tiene productos asociados" y
        // nunca aplicaban. La semántica de un cupón sin restricción es "todo".)
        List<CouponProduct> couponProducts = coupon.getProducts();
        boolean general = couponProducts == null || couponProducts.isEmpty();
        Set<String> eligibleProductIds = general
                ? Set.of()
                : couponProducts.stream().map(CouponProduct::getProductId).collect(Collectors.toSet());
        BigDecimal baseAmount = BigDecimal.ZERO;
        List<String> appliedProductIds = command.items().stream()
            .filter(item -> item.productId() != null && (general || eligibleProductIds.contains(item.productId())))
            .map(OrderItemDto::productId).collect(Collectors.toList());
        for (OrderItemDto item : command.items()) {
            if (item.productId() == null || (!general && !eligibleProductIds.contains(item.productId()))) continue;
            BigDecimal itemTotal = item.unitPrice().multiply(BigDecimal.valueOf(item.quantity()));
            baseAmount = baseAmount.add(itemTotal);
        }
        if (baseAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return this.createInvalidResult("El cupón no aplica: no hay productos elegibles en la orden");
        }
        BigDecimal discountAmount = baseAmount.multiply(coupon.getDiscountPercentage()).divide(BigDecimal.valueOf(100L), 2, RoundingMode.HALF_UP);
        BigDecimal newSubtotal = command.subtotal().subtract(discountAmount);
        if (newSubtotal.compareTo(BigDecimal.ZERO) < 0) {
            newSubtotal = BigDecimal.ZERO;
        }
        String message = String.format("Se aplicó el cupón %s: %s%% de descuento %s. Descuento total: $%s",
                coupon.getCode().toUpperCase(), coupon.getDiscountPercentage(),
                general ? "en toda la orden" : "en productos seleccionados", discountAmount);
        if (coupon.getName() != null && !coupon.getName().isEmpty()) {
            message = message + " (" + coupon.getName() + ")";
        }
        return new ApplyDiscountResult(true, coupon.getCode(), coupon.getDiscountPercentage(), discountAmount, newSubtotal, message, appliedProductIds);
    }
    @Override
    @Transactional
    public void linkOrderWithCoupon(LinkOrderCouponCommand command) {
        if (command.orderId() == null) {
            throw new IllegalArgumentException("El ID de la orden es requerido");
        }
        if (command.code() == null || command.code().trim().isEmpty()) {
            throw new IllegalArgumentException("El código del cupón es requerido");
        }
        Optional<DiscountCoupon> couponOpt = this.couponRepositoryPort.findByCodeIgnoreCase(command.code());
        if (couponOpt.isEmpty()) {
            throw new IllegalArgumentException("El cupón no existe: " + command.code());
        }
        DiscountCoupon coupon = couponOpt.get();
        Optional<DiscountUsage> existingUsage = this.usageRepositoryPort.findByOrderIdAndCouponId(command.orderId(), coupon.getId());
        if (existingUsage.isPresent()) {
            throw new IllegalStateException("El cupón ya fue aplicado a esta orden");
        }
        DiscountUsage usage = new DiscountUsage();
        usage.setOrderId(command.orderId());
        usage.setCoupon(coupon);
        usage.setDiscountCode(coupon.getCode());
        usage.setSubtotalBeforeDiscount(command.subtotalBeforeDiscount());
        usage.setDiscountAmount(command.discountAmount());
        usage.setTotalAfterDiscount(command.totalAfterDiscount());
        usage.setCreatedAt(LocalDateTime.now(BOGOTA_ZONE));
        DiscountUsage savedUsage = this.usageRepositoryPort.save(usage);
        saveDiscountUsageToOutbox(savedUsage);
    }
    private void saveDiscountUsageToOutbox(DiscountUsage usage) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("eventType", "DISCOUNT_USAGE_CREATED");
            payload.put("usage", usage);
            SyncOutbox outbox = new SyncOutbox();
            outbox.setAggregateType("DISCOUNT_USAGE");
            outbox.setAggregateId(usage.getId());
            outbox.setEventType("DISCOUNT_USAGE_CREATED");
            outbox.setPayloadJson(objectMapper.writeValueAsString(payload));
            outbox.setStatus("PENDING");
            outbox.setAttempts(0);
            outbox.setNextRetryAt(System.currentTimeMillis());
            outbox.setCreatedAt(System.currentTimeMillis());
            outbox.setUpdatedAt(System.currentTimeMillis());
            syncOutboxRepositoryPort.save(outbox);
        } catch (Exception e) {
            logger.error("Error encolando sincronización de uso de descuento: {}", e.getMessage());
        }
    }
    private ApplyDiscountResult createInvalidResult(String message) {
        return new ApplyDiscountResult(false, null, null, BigDecimal.ZERO, BigDecimal.ZERO, message, List.of());
    }
    @Override
    @Transactional
    public DiscountCoupon createCoupon(DiscountCoupon coupon, List<ProductDiscountDto> products) {
        if (this.couponRepositoryPort.existsByCode(coupon.getCode())) {
            throw new IllegalArgumentException("Ya existe un cupón con el código: " + coupon.getCode());
        }
        this.validateCouponData(coupon, products);
        coupon.setCreatedAt(LocalDateTime.now(BOGOTA_ZONE));
        coupon.setUpdatedAt(LocalDateTime.now(BOGOTA_ZONE));
        DiscountCoupon savedCoupon = this.couponRepositoryPort.save(coupon);
        saveCouponToOutbox(savedCoupon);
        if (products != null && !products.isEmpty()) {
            for (ProductDiscountDto productDto : products) {
                CouponProduct couponProduct = new CouponProduct();
                couponProduct.setCoupon(savedCoupon);
                couponProduct.setProductId(productDto.productId());
                couponProduct.setProductName(productDto.productName());
                CouponProduct savedCP = this.couponProductRepositoryPort.save(couponProduct);
                saveCouponProductToOutbox(savedCP);
            }
        }
        return savedCoupon;
    }
    @Override
    @Transactional
    public DiscountCoupon updateCoupon(Long id, DiscountCoupon updatedData, List<ProductDiscountDto> products) {
        DiscountCoupon existing = this.couponRepositoryPort.findById(id).orElseThrow(() -> new IllegalArgumentException("Cupón no encontrado con ID: " + id));
        existing.setCode(updatedData.getCode());
        existing.setName(updatedData.getName());
        existing.setDescription(updatedData.getDescription());
        existing.setDiscountPercentage(updatedData.getDiscountPercentage());
        existing.setValidFrom(updatedData.getValidFrom());
        existing.setValidTo(updatedData.getValidTo());
        existing.setValidWeekdays(updatedData.getValidWeekdays());
        existing.setIsActive(updatedData.getIsActive());
        existing.setUpdatedAt(LocalDateTime.now(BOGOTA_ZONE));
        this.validateCouponData(existing, products);
        DiscountCoupon savedCoupon = this.couponRepositoryPort.save(existing);
        saveCouponToOutbox(savedCoupon);
        this.couponProductRepositoryPort.deleteByCouponId(id);
        if (products != null && !products.isEmpty()) {
            for (ProductDiscountDto productDto : products) {
                CouponProduct couponProduct = new CouponProduct();
                couponProduct.setCoupon(savedCoupon);
                couponProduct.setProductId(productDto.productId());
                couponProduct.setProductName(productDto.productName());
                CouponProduct savedCP = this.couponProductRepositoryPort.save(couponProduct);
                saveCouponProductToOutbox(savedCP);
            }
        }
        return savedCoupon;
    }
    @Override
    @Transactional
    public DiscountCoupon deactivateCoupon(Long id) {
        DiscountCoupon coupon = this.couponRepositoryPort.findById(id).orElseThrow(() -> new IllegalArgumentException("Cupón no encontrado con ID: " + id));
        coupon.setIsActive(false);
        coupon.setUpdatedAt(LocalDateTime.now(BOGOTA_ZONE));
        DiscountCoupon savedCoupon = this.couponRepositoryPort.save(coupon);
        saveCouponToOutbox(savedCoupon);
        return savedCoupon;
    }
    private void saveCouponToOutbox(DiscountCoupon coupon) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("eventType", "COUPON_SAVED");
            payload.put("coupon", coupon);
            SyncOutbox outbox = new SyncOutbox();
            outbox.setAggregateType("COUPON");
            outbox.setAggregateId(coupon.getId());
            outbox.setEventType("COUPON_SAVED");
            outbox.setPayloadJson(objectMapper.writeValueAsString(payload));
            outbox.setStatus("PENDING");
            outbox.setAttempts(0);
            outbox.setNextRetryAt(System.currentTimeMillis());
            outbox.setCreatedAt(System.currentTimeMillis());
            outbox.setUpdatedAt(System.currentTimeMillis());
            syncOutboxRepositoryPort.save(outbox);
        } catch (Exception e) {
            logger.error("Error encolando sincronización de cupón: {}", e.getMessage());
        }
    }
    @Override
    public Page<DiscountCoupon> listAllCoupons(String status, Pageable pageable) {
        LocalDate today = LocalDate.now(BOGOTA_ZONE);
        if (status == null || status.equalsIgnoreCase("all")) {
            return this.couponRepositoryPort.findAll(pageable);
        }
        if (status.equalsIgnoreCase("active")) {
            return this.couponRepositoryPort.findCurrentlyActive(today, pageable);
        }
        if (status.equalsIgnoreCase("inactive")) {
            return this.couponRepositoryPort.findByIsActive(false, pageable);
        }
        if (status.equalsIgnoreCase("expired")) {
            return this.couponRepositoryPort.findExpired(today, pageable);
        }
        return this.couponRepositoryPort.findAll(pageable);
    }
    @Override
    @Transactional
    public void deleteCoupon(Long id) {
        DiscountCoupon coupon = this.couponRepositoryPort.findById(id).orElseThrow(() -> new IllegalArgumentException("Cupón no encontrado con ID: " + id));
        List<DiscountUsage> usages = this.usageRepositoryPort.findByCouponId(id);
        if (!usages.isEmpty()) {
            throw new IllegalStateException("No se puede eliminar el cupón porque tiene " + usages.size() + " uso(s) registrado(s). Considere desactivarlo en su lugar.");
        }
        this.couponProductRepositoryPort.deleteByCouponId(id);
        this.couponRepositoryPort.delete(coupon);
    }
    private void validateCouponData(DiscountCoupon coupon, List<ProductDiscountDto> products) {
        if (coupon.getValidFrom() != null && coupon.getValidTo() != null && coupon.getValidFrom().isAfter(coupon.getValidTo())) {
            throw new IllegalArgumentException("La fecha de inicio no puede ser posterior a la fecha de fin");
        }
    }
    private void saveCouponProductToOutbox(CouponProduct cp) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("eventType", "COUPON_PRODUCT_SAVED");
            payload.put("id", cp.getId());
            payload.put("couponId", cp.getCoupon().getId());
            payload.put("productId", cp.getProductId());
            payload.put("productName", cp.getProductName());
            SyncOutbox outbox = new SyncOutbox();
            outbox.setAggregateType("COUPON_PRODUCT");
            outbox.setAggregateId(cp.getId());
            outbox.setEventType("COUPON_PRODUCT_SAVED");
            outbox.setPayloadJson(objectMapper.writeValueAsString(payload));
            outbox.setStatus("PENDING");
            outbox.setAttempts(0);
            outbox.setNextRetryAt(System.currentTimeMillis());
            outbox.setCreatedAt(System.currentTimeMillis());
            outbox.setUpdatedAt(System.currentTimeMillis());
            syncOutboxRepositoryPort.save(outbox);
        } catch (Exception e) {
            logger.error("Error encolando sincronización de producto de cupón: {}", e.getMessage());
        }
    }
}
