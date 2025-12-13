/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.suresell.order.exception.AdminPasswordException
 *  com.suresell.order.serivices.AdminPasswordValidator
 *  org.slf4j.Logger
 *  org.slf4j.LoggerFactory
 *  org.springframework.beans.factory.annotation.Value
 *  org.springframework.stereotype.Service
 */
package com.suresell.order.serivices;
import com.suresell.order.exception.AdminPasswordException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
@Service
public class AdminPasswordValidator {
    private static final Logger logger = LoggerFactory.getLogger(AdminPasswordValidator.class);
    @Value(value="${coupon.admin.password}")
    private String adminPassword;
    public boolean isValidAdminPassword(String providedPassword) {
        if (providedPassword == null || providedPassword.trim().isEmpty()) {
            logger.warn("Intento de acceso admin sin contrase\u00f1a");
            return false;
        }
        boolean isValid = this.adminPassword.equals(providedPassword);
        if (!isValid) {
            logger.warn("Intento de acceso admin con contrase\u00f1a incorrecta");
        } else {
            logger.info("Acceso admin validado exitosamente");
        }
        return isValid;
    }
    public void validateAdminPasswordOrThrow(String providedPassword) {
        if (!this.isValidAdminPassword(providedPassword)) {
            throw new AdminPasswordException("Contrase\u00f1a de administrador incorrecta o ausente");
        }
    }
}
