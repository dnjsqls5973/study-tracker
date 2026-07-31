package com.wonbin.study_tracker.domain.user.service;

public interface GoogleIdentityResolver {
    GoogleIdentity resolveFromIdToken(String idToken);
    GoogleIdentity resolveFromAccessToken(String accessToken);
}
