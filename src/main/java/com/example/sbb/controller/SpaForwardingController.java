package com.example.sbb.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 클라이언트 사이드 라우트(React Router)를 SPA 진입점으로 포워딩합니다.
 * /team/1/calendar 같은 직접 접속도 404 대신 index.html을 반환하도록 보장합니다.
 */
@Controller
public class SpaForwardingController {

    /**
     * API, 정적 리소스, 웹소켓이 아닌 모든 경로를 index.html로 포워딩합니다.
     * - /api, /ws 로 시작하는 경로는 제외
     * - 일반적인 정적 리소스 폴더와 확장자를 가진 실제 파일 요청은 제외
     */
    @GetMapping(value = {
            // 제외: api, ws, 정적 폴더 및 점(.)을 포함한 실제 파일 경로(index.html, *.js, *.css 등)
            "/{path:^(?!api|ws|assets|static|swagger-ui|v3|actuator|db|js)(?!.*\\.).*$}",
            "/{path:^(?!api|ws|assets|static|swagger-ui|v3|actuator|db|js)(?!.*\\.).*$}/**"
    })
    public String forwardSpaRoutes() {
        return "forward:/index.html";
    }
}


