package com.raf.si.Banka2Backend.services;

import com.raf.si.Banka2Backend.exceptions.PasswordResetTokenNotFoundException;
import com.raf.si.Banka2Backend.exceptions.UserNotFoundException;
import com.raf.si.Banka2Backend.models.users.PasswordResetToken;
import com.raf.si.Banka2Backend.models.users.Permission;
import com.raf.si.Banka2Backend.models.users.User;
import com.raf.si.Banka2Backend.repositories.users.PasswordResetTokenRepository;
import com.raf.si.Banka2Backend.repositories.users.UserRepository;
import com.raf.si.Banka2Backend.services.interfaces.UserServiceInterface;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class UserService implements UserDetailsService, UserServiceInterface {
  private final UserRepository userRepository;
  private final PasswordResetTokenRepository passwordResetTokenRepository;

  @Autowired
  public UserService(
      UserRepository userRepository, PasswordResetTokenRepository passwordResetTokenRepository) {
    this.userRepository = userRepository;
    this.passwordResetTokenRepository = passwordResetTokenRepository;
  }

  @Override
  public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
    Optional<User> myUser = this.findByEmail(username);
    if (myUser.isEmpty()) {
      throw new UsernameNotFoundException("User with email: " + username + " not found");
    }

    return new org.springframework.security.core.userdetails.User(
        myUser.get().getEmail(), myUser.get().getPassword(), new ArrayList<>());
  }

  @Override
  public Optional<User> findByEmail(String email) {
    return userRepository.findUserByEmail(email);
  }

  @Override
  public List<User> findAll() {
    return userRepository.findAll();
  }

  @Override
  public User save(User user) {
    return userRepository.save(user);
  }

  @Override
  public List<Permission> getUserPermissions(String email) {
    List<Permission> permissions =
        new ArrayList<>(userRepository.findUserByEmail(email).get().getPermissions());
    return permissions;
  }

  @Override
  public Optional<User> findById(Long id) throws UserNotFoundException {

    Optional<User> user = userRepository.findById(id);

    if (user.isPresent()) {
      return user;
    } else {
      throw new UserNotFoundException(id);
    }
  }

  @Override
  public void deleteById(Long id) throws UserNotFoundException {

    try {
      userRepository.deleteById(id);
    } catch (NoSuchElementException e) {
      throw new UserNotFoundException(id);
    }
  }

  @Override
  public Optional<User> getUserByPasswordResetToken(String token) {
    Optional<PasswordResetToken> passwordResetToken =
        this.passwordResetTokenRepository.findPasswordResetTokenByToken(token);
    if (passwordResetToken.isEmpty()) return null;
    return this.userRepository.findById(passwordResetToken.get().getUser().getId());
  }

  @Override
  public void changePassword(User user, String newPassword, String passwordResetToken) {
    user.setPassword(newPassword);

    Optional<PasswordResetToken> passwordResetTokenFromDB =
        passwordResetTokenRepository.findPasswordResetTokenByToken(passwordResetToken);

    if (passwordResetTokenFromDB.isPresent()) {
      Optional<User> userFromDB = userRepository.findById(user.getId());

      if (userFromDB.isPresent()) {
        User userToChangePasswordTo = userFromDB.get();
        userToChangePasswordTo.setPassword(newPassword);

        userRepository.save(user);
      } else {
        throw new UserNotFoundException(user.getId());
      }

      passwordResetTokenRepository.deleteByToken(passwordResetToken);
    } else {
      throw new PasswordResetTokenNotFoundException(passwordResetToken);
    }
  }
}
