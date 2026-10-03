package com.handsoap.config;

import com.handsoap.entity.Admin;
import com.handsoap.repository.AdminRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * 讓 Spring Security 可以從 admins 資料表載入管理員帳號進行登入驗證。
 */
@Service
public class AdminUserDetailsService implements UserDetailsService {

    private final AdminRepository adminRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * 供「帳號不存在」分支使用的假密碼雜湊（review H5，消除帳號列舉）。
     *
     * <p><b>刻意在啟動時即時產生，而不是寫死一個 hash 常數。</b>兩個原因：</p>
     *
     * <ol>
     *   <li><b>確保 cost 與正式 encoder 一致。</b>BCrypt 的運算量由 hash 字串裡的
     *       cost 參數決定。若這裡寫死 {@code $2a$10$...} 而正式 encoder 是
     *       {@code BCryptPasswordEncoder(12)}，dummy 比對的耗時只有真實比對的
     *       1/16（cost 每 +1 約為 4 倍）—— 消除帳號列舉的效果會直接失效，
     *       而且沒有任何編譯錯誤或執行期錯誤可以發現。
     *       由同一個 encoder 產生，兩者 cost 必然一致。</li>
     *   <li><b>原始碼不含任何 hash 字面值。</b>寫死的 hash 會被專案的
     *       {@code scripts/verify-no-secrets.ps1} 當成疑似外洩的密碼雜湊擋下
     *       （該規則刻意不做豁免，因為豁免只會讓真實外洩漏抓）。
     *       改為執行期產生後，程式碼裡不會出現 {@code $2a$} 之類的字串。</li>
     * </ol>
     *
     * <p>另外，用 {@link UUID#randomUUID()} 產生明文也確保這個雜湊對應的
     * 密碼無人知曉 —— 寫死的公開測試向量（BCrypt 社群廣為流傳的那組）一旦
     * 被誤當成有效密碼寫進資料庫，就等同公開金鑰。</p>
     */
    private final String dummyHash;

    public AdminUserDetailsService(AdminRepository adminRepository, PasswordEncoder passwordEncoder) {
        this.adminRepository = adminRepository;
        this.passwordEncoder = passwordEncoder;
        this.dummyHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Admin admin = adminRepository.findByUsername(username).orElse(null);

        if (admin == null) {
            // review H5：消除帳號列舉（username enumeration）。
            //
            // 為什麼需要：帳號「存在」時會走完 BCrypt 比對（cost 12 約需數百毫秒），
            // 「不存在」時原本是立刻丟例外（近乎 0 毫秒）。攻擊者即使看不到錯誤訊息
            // （GlobalExceptionHandler 已統一回 "Invalid username or password"），
            // 仍可用回應時間差精確列舉出哪些管理員帳號真的存在。
            // 這點特別關鍵，因為登入限流的 key 正是 username —— 能列出帳號名稱，
            // 攻擊者就能鎖死別人的帳號（見 LoginAttemptService 的說明）。
            //
            // 刻意丟出與「密碼錯誤」相同的 UsernameNotFoundException：Spring Security
            // 的 DaoAuthenticationProvider 會把它轉成 BadCredentialsException，
            // 因此對外的錯誤訊息與真實密碼錯誤完全一致。
            dummyPasswordMatch();
            throw new UsernameNotFoundException("Admin not found: " + username);
        }

        return new AdminPrincipal(admin);
    }

    /**
     * 花掉一次 BCrypt 比對的時間，讓「帳號不存在」的分支與「密碼錯誤」的耗時拉近。
     *
     * <p>比對結果（true/false）刻意忽略 —— 這裡只在乎時間成本。
     * 傳入的明文刻意不可能命中 {@link #dummyHash}（該雜湊的明文是隨機 UUID）。</p>
     */
    private void dummyPasswordMatch() {
        passwordEncoder.matches("never-matches-this-value", dummyHash);
    }
}