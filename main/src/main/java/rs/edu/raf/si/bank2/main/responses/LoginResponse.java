package rs.edu.raf.si.bank2.main.responses;

import java.util.List;
import lombok.Data;
import rs.edu.raf.si.bank2.main.models.mariadb.Permission;

@Data
public class LoginResponse {
    private String token;
    private List<Permission> permissions;

    public LoginResponse(String token, List<Permission> permissions) {
        this.token = token;
        this.permissions = permissions;
    }
}
