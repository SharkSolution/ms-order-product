package com.suresell.orders.application.dto;
import com.suresell.orders.domain.model.PagerColor;  
import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PagerAvailabilityDto {
    private PagerColor color;
    private String number;
    private boolean available;
}
