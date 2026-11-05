package com.qiao.account.web;

public record LoginResponse(String token, MeView user) {
}
