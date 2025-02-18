package org.homio.app.auth;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.homio.api.entity.UserEntity;
import org.homio.api.entity.types.IdentityEntity;
import org.homio.api.model.Icon;
import org.homio.api.ui.field.action.ActionInputParameter;
import org.homio.api.util.HardwareUtils;
import org.homio.api.util.NotificationLevel;
import org.homio.app.manager.common.ContextImpl;
import org.homio.app.model.entity.user.UserGuestEntity;
import org.homio.app.setting.system.SystemClearCacheButtonSetting;
import org.homio.app.setting.system.SystemLogoutButtonSetting;
import org.homio.app.setting.system.auth.SystemDisableAuthTokenOnRestartSetting;
import org.homio.app.setting.system.auth.SystemJWTTokenValidSetting;
import org.homio.app.spring.ContextCreated;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import static org.homio.api.util.HardwareUtils.MACHINE_IP_ADDRESS;

@Log4j2
@Component
@RequiredArgsConstructor
public class JwtTokenProvider implements ContextCreated {

  private final UserEntityDetailsService userEntityDetailsService;
  private final Object NULL = new Object();
  private final Map<String, Authentication> userCache = new ConcurrentHashMap<>();
  private final Map<String, Object> blockedTokens = new ConcurrentHashMap<>();
  private JwtParser jwtParser;
  private boolean regenerateSecurityIdOnRestart;
  private @Getter int jwtValidityTimeout;
  private byte[] securityId;

  private static void addCreateGuestAccessToken(ContextImpl context) {
    context.ui().addHeaderMenuButton("access", new Icon("fas fa-key"), IdentityEntity.class);
    context.ui().headerButtonBuilder("add-access").title("Create guest access token").icon(new Icon("fas fa-key"))
      .clickAction(() -> {
        context.ui().dialog().sendDialogRequest("add-access-dialog", "ADD_ANONYMOUS_ACCESS",
          (responseType, pressedButton, parameters) -> {
            String userName = parameters.get("user").asText();
            String password = parameters.get("password").asText();
            UserGuestEntity entity = new UserGuestEntity();
            entity.setName(userName);
            entity.setPassword(password);
            entity.setIeeeAddress("guest@mail.com");
            entity.setEmail("guest@mail.com");
            entity.setJsonData("ip", MACHINE_IP_ADDRESS);
            context.db().save(entity);
            String url = UserGuestEntity.getAccessURL(entity);
            context.ui().toastr().sendMessage("Access URL", url, NotificationLevel.success, 60);
          }, dialogEditor -> {
            dialogEditor.disableKeepOnUi();
            dialogEditor.appearance(new Icon("fas fa-users"), null);
            List<ActionInputParameter> inputs = new ArrayList<>();
            inputs.add(ActionInputParameter.textRequired("user", "", 3, 20));
            inputs.add(ActionInputParameter.textRequired("password", "", 3, 20));
            dialogEditor.submitButton("Create URL", button -> {
            }).group("General", inputs);
          });
        return null;
      }).attachToHeaderMenu("access").build();
  }

  @Override
  public void onContextCreated(ContextImpl context) throws Exception {
    context.setting().listenValue(SystemClearCacheButtonSetting.class, "jwt-clear-cache", () -> {
      userCache.clear();
      blockedTokens.clear();
    });

    addCreateGuestAccessToken(context);

    this.jwtValidityTimeout = context.setting().getValue(SystemJWTTokenValidSetting.class);
    this.regenerateSecurityIdOnRestart = context.setting().getValue(SystemDisableAuthTokenOnRestartSetting.class);
    regenerateSecurityID(context);
    context.setting().listenValue(SystemJWTTokenValidSetting.class, "jwt-valid", value -> {
      this.jwtValidityTimeout = value;
      regenerateSecurityID(context);
      log.info("Generated securityID: {} on change timeout: {}", securityId, value);
    });
    context.setting().listenValue(SystemDisableAuthTokenOnRestartSetting.class, "jwt-req-app", value -> {
      this.regenerateSecurityIdOnRestart = value;
      regenerateSecurityID(context);
      log.info("Generated securityID: {} on disable auth on restart: {}", securityId, value);
    });
    context.setting().listenValue(SystemLogoutButtonSetting.class, "logout", () -> {
      UserEntity user = context.user().getLoggedInUser();
      if (user != null) {
        boolean removed = userCache.entrySet().removeIf(entry -> {
          if (user.getEntityID().equals(UserEntityDetailsService.getEntityID(entry.getValue()))) {
            blockedTokens.put(entry.getKey(), NULL);
            return true;
          }
          return false;
        });
        if (removed) {
          user.logInfo("logged out");
          context.ui().notification().removeBlock("user-" + user.getEmail());
          context.ui().dialog().reloadWindow("sys.auth_changed");
        }
      }
    });
  }

  public void revokeToken(String token) {
    userCache.remove(token);
  }

  public Authentication getAuthentication(String token) {
    return userCache.computeIfAbsent(token, key -> {
      removeOutdatedTokens();

      String userName = getUsername(key);
      UserDetails userDetails = userEntityDetailsService.loadUserByUsername(userName);
      return new UsernamePasswordAuthenticationToken(userDetails,
        userDetails.getUsername(), userDetails.getAuthorities());
    });
  }

  public boolean validateToken(String token) {
    // in case if postConstruct not yet fired but ui send request to backend
    if (this.jwtParser == null) {
      return false;
    }
    if (blockedTokens.containsKey(token)) {
      throw new ExpiredJwtException(null, null, "Token is expired");
    }
    return isTokenValid(token);
  }

  public String resolveToken(String bearerToken) {
    if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
      return bearerToken.substring(7);
    }
    return null;
  }

    /*public String generateUserId(String accessID, String password) {
        AccessUrlRequest request = accessUrlRequests
            .values()
            .stream()
            .filter(f -> f.accessID.equals(accessID))
            .findAny().orElse(null);
        if (request != null) {
            if (!request.password.equals(password)) {
                throw new IllegalArgumentException("Wrong password");
            }
            accessUrlRequests.remove(request.userName);
            context.ui().removeHeaderButton("access-" + request.accessID);
            Collection<? extends GrantedAuthority> authentication = null;
            String userToken = createToken(request.userName, authentication, TimeUnit.DAYS.toMillis(365));
            // we should save it to database
            return userToken;
        }
        return null;
    }*/

  public String createToken(String username, Authentication authentication) {
    long validMillis = TimeUnit.MINUTES.toMillis(jwtValidityTimeout);
    String token = createToken(username, authentication.getAuthorities(), validMillis);
    userCache.put(token, authentication);
    removeOutdatedTokens();
    return token;
  }

  private String createToken(String username, Collection<? extends GrantedAuthority> auth, long validMillis) {
    Date now = new Date();
    Date validity = new Date(now.getTime() + validMillis);
    return Jwts.builder()
      .setId(UUID.randomUUID().toString())
      .setAudience(username)
      .claim("auth", auth)
      .setIssuedAt(now)
      .setIssuer("homio_app")
      .setExpiration(validity)
      .signWith(SignatureAlgorithm.HS256, securityId)
      .compact();
  }

  private void regenerateSecurityID(ContextImpl context) {
    this.securityId = buildSecurityId();
    this.jwtParser = Jwts.parser().setSigningKey(securityId).requireIssuer("homio_app");
    context.ui().dialog().reloadWindow("sys.auth_changed");
  }

  private void removeOutdatedTokens() {
    blockedTokens.keySet().removeIf(blockToken -> !isTokenValid(blockToken));
  }

  private boolean isTokenValid(String token) {
    try {
      this.jwtParser.parseClaimsJws(token);
      return true;
    } catch (JwtException | IllegalArgumentException e) {
      return false;
    }
  }

  private String getUsername(String token) {
    return this.jwtParser.parseClaimsJws(token).getBody().getAudience();
  }

  private byte[] buildSecurityId() {
    userCache.clear();
    String securityId = HardwareUtils.APP_ID + "_" + jwtValidityTimeout;
    if (regenerateSecurityIdOnRestart) {
      securityId += "_" + HardwareUtils.RUN_COUNT;
    }
    return Base64.getEncoder().encode(securityId.getBytes());
  }
}
