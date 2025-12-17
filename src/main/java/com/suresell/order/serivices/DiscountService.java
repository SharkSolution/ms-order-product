/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.suresell.order.model.entity.CouponProduct
 *  com.suresell.order.model.entity.DiscountCoupon
 *  com.suresell.order.model.entity.DiscountUsage
 *  com.suresell.order.model.record.ApplyDiscountCommand
 *  com.suresell.order.model.record.ApplyDiscountResult
 *  com.suresell.order.model.record.LinkOrderCouponCommand
 *  com.suresell.order.model.record.OrderItemDto
 *  com.suresell.order.model.record.ProductDiscountDto
 *  com.suresell.order.repository.CouponProductRepository
 *  com.suresell.order.repository.DiscountCouponRepository
 *  com.suresell.order.repository.DiscountUsageRepository
 *  com.suresell.order.serivices.AdminPasswordValidator
 *  com.suresell.order.serivices.DiscountService
 *  org.slf4j.Logger
 *  org.slf4j.LoggerFactory
 *  org.springframework.beans.factory.annotation.Autowired
 *  org.springframework.stereotype.Service
 *  org.springframework.transaction.annotation.Transactional
 */
package com.suresell.order.serivices;
import com.suresell.order.model.entity.CouponProduct;
import com.suresell.order.model.entity.DiscountCoupon;
import com.suresell.order.model.entity.DiscountUsage;
import com.suresell.order.model.record.ApplyDiscountCommand;
import com.suresell.order.model.record.ApplyDiscountResult;
import com.suresell.order.model.record.LinkOrderCouponCommand;
import com.suresell.order.model.record.OrderItemDto;
import com.suresell.order.model.record.ProductDiscountDto;
import com.suresell.order.repository.CouponProductRepository;
import com.suresell.order.repository.DiscountCouponRepository;
import com.suresell.order.repository.DiscountUsageRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class DiscountService {
    private static final Logger logger = LoggerFactory.getLogger(DiscountService.class);
    @Autowired
    private DiscountCouponRepository couponRepository;
    @Autowired
    private DiscountUsageRepository usageRepository;
    @Autowired
    private CouponProductRepository couponProductRepository;
    private static final ZoneId BOGOTA_ZONE = ZoneId.of("America/Bogota");
    
    // @Autowired // Removed as per user request
    // private AdminPasswordValidator passwordValidator; // Removed as per user request

    public ApplyDiscountResult applyDiscount(ApplyDiscountCommand command) {
        LocalDate orderDate;
        logger.info("Aplicando cup\u00f3n: {}", (Object)command.code());
        if (command.code() == null || command.code().trim().isEmpty()) {
            return this.createInvalidResult("El c\u00f3digo del cup\u00f3n es requerido");
        }
        if (command.subtotal() == null || command.subtotal().compareTo(BigDecimal.ZERO) <= 0) {
            return this.createInvalidResult("El subtotal debe ser mayor a cero");
        }
        if (command.items() == null || command.items().isEmpty()) {
            return this.createInvalidResult("La orden debe tener al menos un producto");
        }
        Optional couponOpt = this.couponRepository.findByCodeIgnoreCase(command.code());
        if (couponOpt.isEmpty()) {
            return this.createInvalidResult("El cup\u00f3n no existe");
        }
        DiscountCoupon coupon = (DiscountCoupon)couponOpt.get();
        if (!Boolean.TRUE.equals(coupon.getIsActive())) {
            return this.createInvalidResult("El cup\u00f3n no est\u00e1 activo");
        }
        LocalDate localDate = orderDate = command.orderDateTime() != null ? command.orderDateTime().toLocalDate() : LocalDate.now(BOGOTA_ZONE);
        if (coupon.getValidFrom() != null && orderDate.isBefore(coupon.getValidFrom())) {
            return this.createInvalidResult("El cup\u00f3n a\u00fan no es v\u00e1lido. V\u00e1lido desde: " + String.valueOf(coupon.getValidFrom()));
        }
        if (coupon.getValidTo() != null && orderDate.isAfter(coupon.getValidTo())) {
            return this.createInvalidResult("El cup\u00f3n ha expirado. V\u00e1lido hasta: " + String.valueOf(coupon.getValidTo()));
        }
        if (coupon.getValidWeekdays() != null && !coupon.getValidWeekdays().trim().isEmpty()) {
            String currentDayStr;
            DayOfWeek currentDay = command.orderDateTime() != null ? command.orderDateTime().getDayOfWeek() : LocalDateTime.now(BOGOTA_ZONE).getDayOfWeek();
            List validDays = Arrays.stream(coupon.getValidWeekdays().split(",")).map(String::trim).map(String::toUpperCase).collect(Collectors.toList());
            if (!validDays.contains(currentDayStr = currentDay.toString().substring(0, 3))) {
                return this.createInvalidResult("El cup\u00f3n no es v\u00e1lido para " + currentDay.toString());
            }
        }
        List<CouponProduct> couponProducts = coupon.getProducts();
        if (couponProducts == null || couponProducts.isEmpty()) {
            return this.createInvalidResult("El cup\u00f3n no tiene productos asociados");
        }
        Set<String> eligibleProductIds = couponProducts.stream().map(cp -> cp.getProductId()).collect(Collectors.toSet());
        BigDecimal baseAmount = BigDecimal.ZERO;
        List<String> appliedProductIds = command.items().stream()
            .filter(item -> item.productId() != null && eligibleProductIds.contains(item.productId()))
            .peek(item -> logger.debug("Producto elegible: {} (ID: {})", (Object)item.productName(), (Object)item.productId()))
            .map(item -> item.productId()).collect(Collectors.toList());

        for (OrderItemDto item2 : command.items()) {
            if (item2.productId() == null || !eligibleProductIds.contains(item2.productId())) continue;
            BigDecimal itemTotal = item2.unitPrice().multiply(BigDecimal.valueOf(item2.quantity().intValue()));
            baseAmount = baseAmount.add(itemTotal);
        }
        if (baseAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return this.createInvalidResult("El cup\u00f3n no aplica: no hay productos elegibles en la orden");
        }
        BigDecimal discountAmount = baseAmount.multiply(coupon.getDiscountPercentage()).divide(BigDecimal.valueOf(100L), 2, RoundingMode.HALF_UP);
        BigDecimal newSubtotal = command.subtotal().subtract(discountAmount);
        if (newSubtotal.compareTo(BigDecimal.ZERO) < 0) {
            newSubtotal = BigDecimal.ZERO;
        }
        Object message = String.format("Se aplic\u00f3 el cup\u00f3n %s: %s%% de descuento en productos seleccionados. Descuento total: $%s", coupon.getCode().toUpperCase(), coupon.getDiscountPercentage(), discountAmount);
        if (coupon.getName() != null && !coupon.getName().isEmpty()) {
            message = (String)message + " (" + coupon.getName() + ")";
        }
        logger.info("Cup\u00f3n aplicado exitosamente. Descuento: ${}", (Object)discountAmount);
        return new ApplyDiscountResult(Boolean.valueOf(true), coupon.getCode(), coupon.getDiscountPercentage(), discountAmount, newSubtotal, (String)message, appliedProductIds);
    }
    @Transactional
    public void linkOrderWithCoupon(LinkOrderCouponCommand command) {
        logger.info("Registrando uso de cup\u00f3n {} para orden {}", (Object)command.code(), (Object)command.orderId());
        if (command.orderId() == null) {
            throw new IllegalArgumentException("El ID de la orden es requerido");
        }
        if (command.code() == null || command.code().trim().isEmpty()) {
            throw new IllegalArgumentException("El c\u00f3digo del cup\u00f3n es requerido");
        }
        Optional couponOpt = this.couponRepository.findByCodeIgnoreCase(command.code());
        if (couponOpt.isEmpty()) {
            throw new IllegalArgumentException("El cup\u00f3n no existe: " + command.code());
        }
        DiscountCoupon coupon = (DiscountCoupon)couponOpt.get();
        Optional existingUsage = this.usageRepository.findByOrderIdAndCouponId(command.orderId(), coupon.getId());
        if (existingUsage.isPresent()) {
            logger.warn("Ya existe un uso del cup\u00f3n {} para la orden {}", (Object)command.code(), (Object)command.orderId());
            throw new IllegalStateException("El cup\u00f3n ya fue aplicado a esta orden");
        }
        DiscountUsage usage = new DiscountUsage();
        usage.setOrderId(command.orderId());
        usage.setCoupon(coupon);
        usage.setDiscountCode(coupon.getCode());
        usage.setSubtotalBeforeDiscount(command.subtotalBeforeDiscount());
        usage.setDiscountAmount(command.discountAmount());
        usage.setTotalAfterDiscount(command.totalAfterDiscount());
        this.usageRepository.save(usage);
        logger.info("Uso de cup\u00f3n registrado exitosamente para orden {}", (Object)command.orderId());
    }
    private ApplyDiscountResult createInvalidResult(String message) {
        return new ApplyDiscountResult(Boolean.valueOf(false), null, null, BigDecimal.ZERO, BigDecimal.ZERO, message, List.of());
    }
    public List<DiscountCoupon> getActiveCoupons() {
        logger.debug("Obteniendo cupones activos");
        LocalDate today = LocalDate.now(BOGOTA_ZONE);
        return this.couponRepository.findByIsActive(Boolean.valueOf(true)).stream().filter(coupon -> {
            if (coupon.getValidTo() != null && today.isAfter(coupon.getValidTo())) {
                return false;
            }
            return coupon.getValidFrom() == null || !today.isBefore(coupon.getValidFrom());
        }).collect(Collectors.toList());
    }
    @Transactional
    public DiscountCoupon createCoupon(DiscountCoupon coupon, List<ProductDiscountDto> products) {
        logger.info("Creando nuevo cup\u00f3n: {}", (Object)coupon.getCode());
        // this.passwordValidator.validateAdminPasswordOrThrow(adminPassword); // Removed as per user request
        if (this.couponRepository.existsByCode(coupon.getCode())) {
            throw new IllegalArgumentException("Ya existe un cup\u00f3n con el c\u00f3digo: " + coupon.getCode());
        }
        this.validateCouponData(coupon, products);
        DiscountCoupon savedCoupon = this.couponRepository.save(coupon);
        if (products != null && !products.isEmpty()) {
            for (ProductDiscountDto productDto : products) {
                CouponProduct couponProduct = new CouponProduct();
                couponProduct.setCoupon(savedCoupon);
                couponProduct.setProductId(productDto.productId());
                couponProduct.setProductName(productDto.productName());
                this.couponProductRepository.save(couponProduct);
            }
        }
        return savedCoupon;
    }
    @Transactional
    public DiscountCoupon updateCoupon(Long id, DiscountCoupon updatedData, List<ProductDiscountDto> products) {
        logger.info("Actualizando cup\u00f3n ID: {}", (Object)id);
        // this.passwordValidator.validateAdminPasswordOrThrow(adminPassword); // Removed as per user request
        DiscountCoupon existing = this.couponRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Cup\u00f3n no encontrado con ID: " + id));
        existing.setCode(updatedData.getCode());
        existing.setName(updatedData.getName());
        existing.setDescription(updatedData.getDescription());
        existing.setDiscountPercentage(updatedData.getDiscountPercentage());
        existing.setValidFrom(updatedData.getValidFrom());
        existing.setValidTo(updatedData.getValidTo());
        existing.setValidWeekdays(updatedData.getValidWeekdays());
        existing.setIsActive(updatedData.getIsActive());
        this.validateCouponData(existing, products);
        this.couponProductRepository.deleteByCouponId(id);
        if (products != null && !products.isEmpty()) {
            for (ProductDiscountDto productDto : products) {
                CouponProduct couponProduct = new CouponProduct();
                couponProduct.setCoupon(existing);
                couponProduct.setProductId(productDto.productId());
                couponProduct.setProductName(productDto.productName());
                this.couponProductRepository.save(couponProduct);
            }
        }
        return this.couponRepository.save(existing);
    }
    @Transactional
    public DiscountCoupon deactivateCoupon(Long id) {
        logger.info("Desactivando cup\u00f3n ID: {}", (Object)id);
        // this.passwordValidator.validateAdminPasswordOrThrow(adminPassword); // Removed as per user request
        DiscountCoupon coupon = this.couponRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Cup\u00f3n no encontrado con ID: " + id));
        coupon.setIsActive(Boolean.valueOf(false));
        return this.couponRepository.save(coupon);
    }
    public List<DiscountCoupon> listAllCoupons(String status) {
        logger.info("Listando cupones con filtro: {}", (Object)status);
        // this.passwordValidator.validateAdminPasswordOrThrow(adminPassword); // Removed as per user request
        if (status == null || status.equalsIgnoreCase("all")) {
            return this.couponRepository.findAll();
        }
        if (status.equalsIgnoreCase("active")) {
            return this.couponRepository.findByIsActive(Boolean.valueOf(true));
        }
        if (status.equalsIgnoreCase("inactive")) {
            return this.couponRepository.findByIsActive(Boolean.valueOf(false));
        }
        if (status.equalsIgnoreCase("expired")) {
            LocalDate today = LocalDate.now(BOGOTA_ZONE);
            return this.couponRepository.findAll().stream().filter(coupon -> coupon.getValidTo() != null && today.isAfter(coupon.getValidTo())).collect(Collectors.toList());
        }
        return this.couponRepository.findAll();
    }
    @Transactional
    public void deleteCoupon(Long id) {
        logger.info("Eliminando cup\u00f3n ID: {}", (Object)id);
        // this.passwordValidator.validateAdminPasswordOrThrow(adminPassword); // Removed as per user request
        DiscountCoupon coupon = this.couponRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Cup\u00f3n no encontrado con ID: " + id));
        List usages = this.usageRepository.findByCouponId(id);
        if (!usages.isEmpty()) {
            logger.warn("No se puede eliminar el cup\u00f3n {} porque tiene {} usos registrados", (Object)coupon.getCode(), (Object)usages.size());
            throw new IllegalStateException("No se puede eliminar el cup\u00f3n porque tiene " + usages.size() + " uso(s) registrado(s). Considere desactivarlo en su lugar.");
        }
        this.couponProductRepository.deleteByCouponId(id);
        this.couponRepository.delete(coupon);
        logger.info("Cup\u00f3n {} eliminado exitosamente", (Object)coupon.getCode());
    }
    private void validateCouponData(DiscountCoupon coupon, List<ProductDiscountDto> products) {
        if (coupon.getCode() == null || coupon.getCode().trim().isEmpty()) {
            throw new IllegalArgumentException("El c\u00f3digo del cup\u00f3n es requerido");
        }
        if (coupon.getName() == null || coupon.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("El nombre del cup\u00f3n es requerido");
        }
        if (coupon.getDiscountPercentage() == null || coupon.getDiscountPercentage().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("El porcentaje de descuento debe ser mayor a cero");
        }
        if (coupon.getDiscountPercentage().compareTo(BigDecimal.valueOf(100L)) > 0) {
            throw new IllegalArgumentException("El descuento porcentual no puede ser mayor al 100%");
        }
        if (products == null || products.isEmpty()) {
            throw new IllegalArgumentException("Debe especificar al menos un producto para el cup\u00f3n");
        }
        for (ProductDiscountDto product : products) {
            if (product.productId() == null) {
                throw new IllegalArgumentException("Todos los productos deben tener un ID");
            }
            if (product.productName() != null && !product.productName().trim().isEmpty()) continue;
            throw new IllegalArgumentException("Todos los productos deben tener un nombre");
        }
        if (coupon.getValidFrom() != null && coupon.getValidTo() != null && coupon.getValidFrom().isAfter(coupon.getValidTo())) {
            throw new IllegalArgumentException("La fecha de inicio no puede ser posterior a la fecha de fin");
        }
    }
}
