package com.example.sbb.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 클라이언트 사이드 라우트(React Router)를 SPA 진입점으로 포워딩
 * /team/1/calendar 같은 직접 접속도 404 대신 index.html을 반환하도록 보장
 */
@Controller
public class SpaForwardingController {

    /**
     * API, 정적 리소스, 웹소켓이 아닌 모든 경로를 index.html로 포워딩
     * - /api, /ws 로 시작하는 경로는 제외
     * - 일반적인 정적 리소스 폴더와 확장자를 가진 실제 파일 요청은 제외
     */
    @GetMapping(value = {
            // 제외: api, ws, 정적 폴더 및 점(.)을 포함한 실제 파일 경로(index.html, *.js, *.css 등)
            // ws 경로는 SockJS가 처리하므로 완전히 제외 (모든 하위 경로 포함)
            "/{path:^(?!api|ws|assets|static|swagger-ui|v3|actuator|db|js)(?!.*\\.).*$}",
            "/{path:^(?!api|ws|assets|static|swagger-ui|v3|actuator|db|js)(?!.*\\.).*$}/**"
    })
    public String forwardSpaRoutes() {
        return "forward:/index.html";
    }
    
    // 명시적으로 /ws/** 경로는 처리하지 않도록 (SockJS가 처리)
    // 이 메서드는 없어도 정규식에서 제외되지만, 명확성을 위해 주석 추가
}


