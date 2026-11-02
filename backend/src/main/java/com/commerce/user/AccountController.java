package com.commerce.user;

import com.commerce.user.UserDtos.*;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/account")
public class AccountController {
    private final AccountService service;

    public AccountController(AccountService service) {
        this.service = service;
    }

    @GetMapping("/me")
    public AccountMe me() {
        return service.me();
    }

    @PatchMapping("/me")
    public AccountMe update(@Valid @RequestBody UpdateProfileRequest body) {
        return service.updateMe(body);
    }

    @PostMapping("/change-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@Valid @RequestBody ChangePasswordRequest body) {
        service.changePassword(body);
    }

    @GetMapping("/addresses")
    public List<AddressResponse> addresses() {
        return service.listAddresses();
    }

    @PostMapping("/addresses")
    @ResponseStatus(HttpStatus.CREATED)
    public AddressResponse create(@Valid @RequestBody AddressRequest body) {
        return service.createAddress(body);
    }

    @PatchMapping("/addresses/{id}")
    public AddressResponse update(@PathVariable Long id, @Valid @RequestBody AddressRequest body) {
        return service.updateAddress(id, body);
    }

    @DeleteMapping("/addresses/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.deleteAddress(id);
    }
}
