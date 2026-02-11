package com.suresell.orders.domain.service;
import com.suresell.orders.domain.model.CouponProduct;
import com.suresell.orders.domain.model.DiscountCoupon;
import com.suresell.orders.domain.model.DiscountUsage;
import com.suresell.orders.application.dto.ApplyDiscountCommand;
import com.suresell.orders.application.dto.ApplyDiscountResult;
import com.suresell.orders.application.dto.LinkOrderCouponCommand;
import com.suresell.orders.application.dto.OrderItemDto;
import com.suresell.orders.application.dto.ProductDiscountDto;
import com.suresell.orders.infrastructure.persistence.DiscountCouponRepository;
import com.suresell.orders.infrastructure.persistence.DiscountUsageRepository;
import com.suresell.orders.domain.port.out.CouponProductRepositoryPort;
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
    private CouponProductRepositoryPort couponProductRepositoryPort;
    private static final ZoneId BOGOTA_ZONE = ZoneId.of("America/Bogota");
    
    public ApplyDiscountResult applyDiscount(ApplyDiscountCommand command) {
        LocalDate orderDate;
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
        return new ApplyDiscountResult(Boolean.valueOf(true), coupon.getCode(), coupon.getDiscountPercentage(), discountAmount, newSubtotal, (String)message, appliedProductIds);
    }
    @Transactional
    public void linkOrderWithCoupon(LinkOrderCouponCommand command) {
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
    }
    private ApplyDiscountResult createInvalidResult(String message) {
        return new ApplyDiscountResult(Boolean.valueOf(false), null, null, BigDecimal.ZERO, BigDecimal.ZERO, message, List.of());
    }
    public List<DiscountCoupon> getActiveCoupons() {
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
                this.couponProductRepositoryPort.save(couponProduct);
            }
        }
        return savedCoupon;
    }
    @Transactional
    public DiscountCoupon updateCoupon(Long id, DiscountCoupon updatedData, List<ProductDiscountDto> products) {
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
        this.couponProductRepositoryPort.deleteByCouponId(id);
        if (products != null && !products.isEmpty()) {
            for (ProductDiscountDto productDto : products) {
                CouponProduct couponProduct = new CouponProduct();
                couponProduct.setCoupon(existing);
                couponProduct.setProductId(productDto.productId());
                couponProduct.setProductName(productDto.productName());
                this.couponProductRepositoryPort.save(couponProduct);
            }
        }
        return this.couponRepository.save(existing);
    }
    @Transactional
    public DiscountCoupon deactivateCoupon(Long id) {
        DiscountCoupon coupon = this.couponRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Cup\u00f3n no encontrado con ID: " + id));
        coupon.setIsActive(Boolean.valueOf(false));
        return this.couponRepository.save(coupon);
    }
    public List<DiscountCoupon> listAllCoupons(String status) {
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
        DiscountCoupon coupon = this.couponRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Cup\u00f3n no encontrado con ID: " + id));
        List usages = this.usageRepository.findByCouponId(id);
        if (!usages.isEmpty()) {
            throw new IllegalStateException("No se puede eliminar el cup\u00f3n porque tiene " + usages.size() + " uso(s) registrado(s). Considere desactivarlo en su lugar.");
        }
        this.couponProductRepositoryPort.deleteByCouponId(id);
        this.couponRepository.delete(coupon);
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
