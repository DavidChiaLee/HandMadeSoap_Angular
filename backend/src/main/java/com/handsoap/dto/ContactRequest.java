package com.handsoap.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ContactRequest {
    @NotBlank(message = "Name is required")
    @Size(max = 100)
    private String name;

    @NotBlank(message = "Email is required")
    @Email(message = "Email should be valid")
    @Size(max = 200)
    private String email;

    @Size(max = 50)
    private String phone;

    @NotBlank(message = "Subject is required")
    @Size(max = 200)
    private String subject;

    @NotBlank(message = "Message is required")
    @Size(max = 2000)
    private String message;

    /**
     * Honeypot 蜜罐欄位（review 728）。
     *
     * <p>刻意<b>不加</b>任何 validation：真人看不到此欄位（畫面上被 off-screen
     * 隱藏），永遠維持 null／空字串；自動填表的 bot 則會視為一般欄位自動填滿。
     * 後端只要發現非空值即判定為 bot —— 不寫庫、回 201 通用訊息
     * （回 400 等於告訴 bot「我識破你了」，讓它換策略）。</p>
     *
     * <p>欄位名刻意用看似正常的 {@code company} 而非 {@code honeypot}／
     * {@code website}：bot 常見的規則會跳過名字含 honeypot/website 的欄位。</p>
     *
     * <p>舊客戶端沒送此欄位 → Jackson 解出 null → 照常放行，向後相容。</p>
     */
    private String company;
}
