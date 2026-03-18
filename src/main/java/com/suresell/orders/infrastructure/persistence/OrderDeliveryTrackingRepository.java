package com.suresell.orders.infrastructure.persistence;
import com.suresell.orders.domain.model.OrderDeliveryTracking;
import org.springframework.data.jpa.repository.JpaRepository;
public interface OrderDeliveryTrackingRepository extends JpaRepository<OrderDeliveryTracking, java.util.UUID> {
}
