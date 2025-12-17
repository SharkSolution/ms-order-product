/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.suresell.order.model.entity.DailyClosure
 *  jakarta.persistence.Column
 *  jakarta.persistence.Entity
 *  jakarta.persistence.Id
 *  jakarta.persistence.Index
 *  jakarta.persistence.PrePersist
 *  jakarta.persistence.Table
 *  lombok.Generated
 */
package com.suresell.order.model.entity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;
import lombok.Generated;
@Entity
@Table(name="daily_closures", indexes={@Index(name="idx_user_name", columnList="user_name"), @Index(name="idx_closing_time", columnList="closing_time")})
public class DailyClosure {
    @Id
    @Column(name="id", columnDefinition="UUID")
    private UUID id;
    @Column(name="user_name", nullable=false, length=100)
    private String userName;
    @Column(name="opening_time", nullable=false)
    private LocalDateTime openingTime;
    @Column(name="closing_time")
    private LocalDateTime closingTime;
    @Column(name="total_expected_cash", precision=15, scale=2)
    private BigDecimal totalExpectedCash;
    @Column(name="total_expected_card", precision=15, scale=2)
    private BigDecimal totalExpectedCard;
    @Column(name="total_expected_nequi", precision=15, scale=2)
    private BigDecimal totalExpectedNequi;
    @Column(name="total_expected_qr", precision=15, scale=2)
    private BigDecimal totalExpectedQr;
    @Column(name="total_counted_cash", precision=15, scale=2)
    private BigDecimal totalCountedCash;
    @Column(name="total_counted_card", precision=15, scale=2)
    private BigDecimal totalCountedCard;
    @Column(name="total_counted_nequi", precision=15, scale=2)
    private BigDecimal totalCountedNequi;
    @Column(name="total_counted_qr", precision=15, scale=2)
    private BigDecimal totalCountedQr;
    @Column(name="difference_amount", precision=15, scale=2)
    private BigDecimal differenceAmount;
    @Column(name="status", length=20)
    private String status;
    @Column(name="notes", columnDefinition="TEXT")
    private String notes;
    private static final ZoneId BOGOTA_ZONE = ZoneId.of("America/Bogota");

    @PrePersist
    public void prePersist() {
        if (this.id == null) {
            this.id = UUID.randomUUID();
        }
        if (this.closingTime == null) {
            this.closingTime = LocalDateTime.now(BOGOTA_ZONE);
        }
    }
    @Generated
    public UUID getId() {
        return this.id;
    }
    @Generated
    public String getUserName() {
        return this.userName;
    }
    @Generated
    public LocalDateTime getOpeningTime() {
        return this.openingTime;
    }
    @Generated
    public LocalDateTime getClosingTime() {
        return this.closingTime;
    }
    @Generated
    public BigDecimal getTotalExpectedCash() {
        return this.totalExpectedCash;
    }
    @Generated
    public BigDecimal getTotalExpectedCard() {
        return this.totalExpectedCard;
    }
    @Generated
    public BigDecimal getTotalExpectedNequi() {
        return this.totalExpectedNequi;
    }
    @Generated
    public BigDecimal getTotalExpectedQr() {
        return this.totalExpectedQr;
    }
    @Generated
    public BigDecimal getTotalCountedCash() {
        return this.totalCountedCash;
    }
    @Generated
    public BigDecimal getTotalCountedCard() {
        return this.totalCountedCard;
    }
    @Generated
    public BigDecimal getTotalCountedNequi() {
        return this.totalCountedNequi;
    }
    @Generated
    public BigDecimal getTotalCountedQr() {
        return this.totalCountedQr;
    }
    @Generated
    public BigDecimal getDifferenceAmount() {
        return this.differenceAmount;
    }
    @Generated
    public String getStatus() {
        return this.status;
    }
    @Generated
    public String getNotes() {
        return this.notes;
    }
    @Generated
    public void setId(UUID id) {
        this.id = id;
    }
    @Generated
    public void setUserName(String userName) {
        this.userName = userName;
    }
    @Generated
    public void setOpeningTime(LocalDateTime openingTime) {
        this.openingTime = openingTime;
    }
    @Generated
    public void setClosingTime(LocalDateTime closingTime) {
        this.closingTime = closingTime;
    }
    @Generated
    public void setTotalExpectedCash(BigDecimal totalExpectedCash) {
        this.totalExpectedCash = totalExpectedCash;
    }
    @Generated
    public void setTotalExpectedCard(BigDecimal totalExpectedCard) {
        this.totalExpectedCard = totalExpectedCard;
    }
    @Generated
    public void setTotalExpectedNequi(BigDecimal totalExpectedNequi) {
        this.totalExpectedNequi = totalExpectedNequi;
    }
    @Generated
    public void setTotalExpectedQr(BigDecimal totalExpectedQr) {
        this.totalExpectedQr = totalExpectedQr;
    }
    @Generated
    public void setTotalCountedCash(BigDecimal totalCountedCash) {
        this.totalCountedCash = totalCountedCash;
    }
    @Generated
    public void setTotalCountedCard(BigDecimal totalCountedCard) {
        this.totalCountedCard = totalCountedCard;
    }
    @Generated
    public void setTotalCountedNequi(BigDecimal totalCountedNequi) {
        this.totalCountedNequi = totalCountedNequi;
    }
    @Generated
    public void setTotalCountedQr(BigDecimal totalCountedQr) {
        this.totalCountedQr = totalCountedQr;
    }
    @Generated
    public void setDifferenceAmount(BigDecimal differenceAmount) {
        this.differenceAmount = differenceAmount;
    }
    @Generated
    public void setStatus(String status) {
        this.status = status;
    }
    @Generated
    public void setNotes(String notes) {
        this.notes = notes;
    }
    @Generated
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof DailyClosure)) {
            return false;
        }
        DailyClosure other = (DailyClosure)o;
        if (!other.canEqual((Object)this)) {
            return false;
        }
        UUID this$id = this.getId();
        UUID other$id = other.getId();
        if (this$id == null ? other$id != null : !((Object)this$id).equals(other$id)) {
            return false;
        }
        String this$userName = this.getUserName();
        String other$userName = other.getUserName();
        if (this$userName == null ? other$userName != null : !this$userName.equals(other$userName)) {
            return false;
        }
        LocalDateTime this$openingTime = this.getOpeningTime();
        LocalDateTime other$openingTime = other.getOpeningTime();
        if (this$openingTime == null ? other$openingTime != null : !((Object)this$openingTime).equals(other$openingTime)) {
            return false;
        }
        LocalDateTime this$closingTime = this.getClosingTime();
        LocalDateTime other$closingTime = other.getClosingTime();
        if (this$closingTime == null ? other$closingTime != null : !((Object)this$closingTime).equals(other$closingTime)) {
            return false;
        }
        BigDecimal this$totalExpectedCash = this.getTotalExpectedCash();
        BigDecimal other$totalExpectedCash = other.getTotalExpectedCash();
        if (this$totalExpectedCash == null ? other$totalExpectedCash != null : !((Object)this$totalExpectedCash).equals(other$totalExpectedCash)) {
            return false;
        }
        BigDecimal this$totalExpectedCard = this.getTotalExpectedCard();
        BigDecimal other$totalExpectedCard = other.getTotalExpectedCard();
        if (this$totalExpectedCard == null ? other$totalExpectedCard != null : !((Object)this$totalExpectedCard).equals(other$totalExpectedCard)) {
            return false;
        }
        BigDecimal this$totalExpectedNequi = this.getTotalExpectedNequi();
        BigDecimal other$totalExpectedNequi = other.getTotalExpectedNequi();
        if (this$totalExpectedNequi == null ? other$totalExpectedNequi != null : !((Object)this$totalExpectedNequi).equals(other$totalExpectedNequi)) {
            return false;
        }
        BigDecimal this$totalExpectedQr = this.getTotalExpectedQr();
        BigDecimal other$totalExpectedQr = other.getTotalExpectedQr();
        if (this$totalExpectedQr == null ? other$totalExpectedQr != null : !((Object)this$totalExpectedQr).equals(other$totalExpectedQr)) {
            return false;
        }
        BigDecimal this$totalCountedCash = this.getTotalCountedCash();
        BigDecimal other$totalCountedCash = other.getTotalCountedCash();
        if (this$totalCountedCash == null ? other$totalCountedCash != null : !((Object)this$totalCountedCash).equals(other$totalCountedCash)) {
            return false;
        }
        BigDecimal this$totalCountedCard = this.getTotalCountedCard();
        BigDecimal other$totalCountedCard = other.getTotalCountedCard();
        if (this$totalCountedCard == null ? other$totalCountedCard != null : !((Object)this$totalCountedCard).equals(other$totalCountedCard)) {
            return false;
        }
        BigDecimal this$totalCountedNequi = this.getTotalCountedNequi();
        BigDecimal other$totalCountedNequi = other.getTotalCountedNequi();
        if (this$totalCountedNequi == null ? other$totalCountedNequi != null : !((Object)this$totalCountedNequi).equals(other$totalCountedNequi)) {
            return false;
        }
        BigDecimal this$totalCountedQr = this.getTotalCountedQr();
        BigDecimal other$totalCountedQr = other.getTotalCountedQr();
        if (this$totalCountedQr == null ? other$totalCountedQr != null : !((Object)this$totalCountedQr).equals(other$totalCountedQr)) {
            return false;
        }
        BigDecimal this$differenceAmount = this.getDifferenceAmount();
        BigDecimal other$differenceAmount = other.getDifferenceAmount();
        if (this$differenceAmount == null ? other$differenceAmount != null : !((Object)this$differenceAmount).equals(other$differenceAmount)) {
            return false;
        }
        String this$status = this.getStatus();
        String other$status = other.getStatus();
        if (this$status == null ? other$status != null : !this$status.equals(other$status)) {
            return false;
        }
        String this$notes = this.getNotes();
        String other$notes = other.getNotes();
        return !(this$notes == null ? other$notes != null : !this$notes.equals(other$notes));
    }
    @Generated
    protected boolean canEqual(Object other) {
        return other instanceof DailyClosure;
    }
    @Generated
    public int hashCode() {
        int PRIME = 59;
        int result = 1;
        UUID $id = this.getId();
        result = result * 59 + ($id == null ? 43 : ((Object)$id).hashCode());
        String $userName = this.getUserName();
        result = result * 59 + ($userName == null ? 43 : $userName.hashCode());
        LocalDateTime $openingTime = this.getOpeningTime();
        result = result * 59 + ($openingTime == null ? 43 : ((Object)$openingTime).hashCode());
        LocalDateTime $closingTime = this.getClosingTime();
        result = result * 59 + ($closingTime == null ? 43 : ((Object)$closingTime).hashCode());
        BigDecimal $totalExpectedCash = this.getTotalExpectedCash();
        result = result * 59 + ($totalExpectedCash == null ? 43 : ((Object)$totalExpectedCash).hashCode());
        BigDecimal $totalExpectedCard = this.getTotalExpectedCard();
        result = result * 59 + ($totalExpectedCard == null ? 43 : ((Object)$totalExpectedCard).hashCode());
        BigDecimal $totalExpectedNequi = this.getTotalExpectedNequi();
        result = result * 59 + ($totalExpectedNequi == null ? 43 : ((Object)$totalExpectedNequi).hashCode());
        BigDecimal $totalExpectedQr = this.getTotalExpectedQr();
        result = result * 59 + ($totalExpectedQr == null ? 43 : ((Object)$totalExpectedQr).hashCode());
        BigDecimal $totalCountedCash = this.getTotalCountedCash();
        result = result * 59 + ($totalCountedCash == null ? 43 : ((Object)$totalCountedCash).hashCode());
        BigDecimal $totalCountedCard = this.getTotalCountedCard();
        result = result * 59 + ($totalCountedCard == null ? 43 : ((Object)$totalCountedCard).hashCode());
        BigDecimal $totalCountedNequi = this.getTotalCountedNequi();
        result = result * 59 + ($totalCountedNequi == null ? 43 : ((Object)$totalCountedNequi).hashCode());
        BigDecimal $totalCountedQr = this.getTotalCountedQr();
        result = result * 59 + ($totalCountedQr == null ? 43 : ((Object)$totalCountedQr).hashCode());
        BigDecimal $differenceAmount = this.getDifferenceAmount();
        result = result * 59 + ($differenceAmount == null ? 43 : ((Object)$differenceAmount).hashCode());
        String $status = this.getStatus();
        result = result * 59 + ($status == null ? 43 : $status.hashCode());
        String $notes = this.getNotes();
        result = result * 59 + ($notes == null ? 43 : $notes.hashCode());
        return result;
    }
    @Generated
    public String toString() {
        return "DailyClosure(id=" + String.valueOf(this.getId()) + ", userName=" + this.getUserName() + ", openingTime=" + String.valueOf(this.getOpeningTime()) + ", closingTime=" + String.valueOf(this.getClosingTime()) + ", totalExpectedCash=" + String.valueOf(this.getTotalExpectedCash()) + ", totalExpectedCard=" + String.valueOf(this.getTotalExpectedCard()) + ", totalExpectedNequi=" + String.valueOf(this.getTotalExpectedNequi()) + ", totalExpectedQr=" + String.valueOf(this.getTotalExpectedQr()) + ", totalCountedCash=" + String.valueOf(this.getTotalCountedCash()) + ", totalCountedCard=" + String.valueOf(this.getTotalCountedCard()) + ", totalCountedNequi=" + String.valueOf(this.getTotalCountedNequi()) + ", totalCountedQr=" + String.valueOf(this.getTotalCountedQr()) + ", differenceAmount=" + String.valueOf(this.getDifferenceAmount()) + ", status=" + this.getStatus() + ", notes=" + this.getNotes() + ")";
    }
    @Generated
    public DailyClosure() {
    }
    @Generated
    public DailyClosure(UUID id, String userName, LocalDateTime openingTime, LocalDateTime closingTime, BigDecimal totalExpectedCash, BigDecimal totalExpectedCard, BigDecimal totalExpectedNequi, BigDecimal totalExpectedQr, BigDecimal totalCountedCash, BigDecimal totalCountedCard, BigDecimal totalCountedNequi, BigDecimal totalCountedQr, BigDecimal differenceAmount, String status, String notes) {
        this.id = id;
        this.userName = userName;
        this.openingTime = openingTime;
        this.closingTime = closingTime;
        this.totalExpectedCash = totalExpectedCash;
        this.totalExpectedCard = totalExpectedCard;
        this.totalExpectedNequi = totalExpectedNequi;
        this.totalExpectedQr = totalExpectedQr;
        this.totalCountedCash = totalCountedCash;
        this.totalCountedCard = totalCountedCard;
        this.totalCountedNequi = totalCountedNequi;
        this.totalCountedQr = totalCountedQr;
        this.differenceAmount = differenceAmount;
        this.status = status;
        this.notes = notes;
    }
}
