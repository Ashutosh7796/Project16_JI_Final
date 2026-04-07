package com.spring.jwt.jwt;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class ActiveSessionService {

	private final Map<String, SessionInfo> usernameToSession = new ConcurrentHashMap<>();

	public SessionInfo replaceActiveSession(String username, String newAccessTokenId, String newRefreshTokenId,
			Instant accessExpiresAt, Instant refreshExpiresAt) {
		if (username == null) {
			return null;
		}
		
		SessionInfo newInfo = new SessionInfo(newAccessTokenId, newRefreshTokenId, accessExpiresAt, refreshExpiresAt, Instant.now());
		SessionInfo previous = usernameToSession.get(username);
		
		if (previous != null) {
			newInfo.setPreviousAccessTokenId(previous.getAccessTokenId());
			newInfo.setPreviousRefreshTokenId(previous.getRefreshTokenId());
		}
		
		usernameToSession.put(username, newInfo);
		log.debug("Active session set for user: {} (access jti: {}, refresh jti: {})", username, shortId(newAccessTokenId), shortId(newRefreshTokenId));
		return previous;
	}

	public void removeActiveSession(String username) {
		if (username != null) {
			usernameToSession.remove(username);
			log.debug("Active session removed for user: {}", username);
		}
	}

	public boolean isCurrentAccessToken(String username, String tokenId) {
		SessionInfo info = usernameToSession.get(username);
		if (info == null) return true; // Gracefully allow if no session in memory (e.g., server restart)
		if (tokenId == null) return false;
		
		if (tokenId.equals(info.getAccessTokenId())) return true;
		
		// 60-second grace period for previous token to prevent race conditions during refresh
		if (tokenId.equals(info.getPreviousAccessTokenId())) {
			if (Instant.now().isBefore(info.getUpdatedAt().plusSeconds(60))) {
				log.debug("Allowed previous access token during grace period for user: {}", username);
				return true;
			}
		}
		return false;
	}

	public boolean isCurrentRefreshToken(String username, String tokenId) {
		SessionInfo info = usernameToSession.get(username);
		if (info == null) return true; // Gracefully allow if no session in memory
		if (tokenId == null) return false;
		
		if (tokenId.equals(info.getRefreshTokenId())) return true;
		
		// 60-second grace period for previous token to prevent race conditions during refresh
		if (tokenId.equals(info.getPreviousRefreshTokenId())) {
			if (Instant.now().isBefore(info.getUpdatedAt().plusSeconds(60))) {
				log.debug("Allowed previous refresh token during grace period for user: {}", username);
				return true;
			}
		}
		return false;
	}

	@Scheduled(fixedRate = 3600000)
	public void cleanupExpiredSessions() {
		Instant now = Instant.now();
		usernameToSession.entrySet().removeIf(e -> {
			SessionInfo s = e.getValue();
			return (s.getRefreshExpiresAt() != null && s.getRefreshExpiresAt().isBefore(now))
				|| (s.getAccessExpiresAt() != null && s.getAccessExpiresAt().isBefore(now));
		});
	}

	private String shortId(String id) {
		if (id == null || id.length() < 6) return "***";
		return id.substring(0, 3) + "..." + id.substring(id.length() - 3);
	}

	@Data
	public static class SessionInfo {
		private final String accessTokenId;
		private final String refreshTokenId;
		private final Instant accessExpiresAt;
		private final Instant refreshExpiresAt;
		private final Instant updatedAt;
		private String previousAccessTokenId;
		private String previousRefreshTokenId;
	}
}

