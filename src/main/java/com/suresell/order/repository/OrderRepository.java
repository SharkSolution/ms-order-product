package com.suresell.order.repository;

import com.suresell.order.model.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

  Optional<Order> findByTableNumberAndStatus(int tableNumber, String status);

  List<Order> findByStatus(String status);
}
