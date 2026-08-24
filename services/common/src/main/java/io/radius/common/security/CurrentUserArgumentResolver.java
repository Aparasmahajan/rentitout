package io.radius.common.security;

import io.radius.common.web.ApiException;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * Lets a controller declare {@code AuthUser me} and get the caller, or a clean
 * 401 when there is none. Nothing reads the user id out of the path or body.
 */
public class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return AuthUser.class.equals(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mav,
                                  NativeWebRequest req, WebDataBinderFactory binderFactory) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthUser user) {
            return user;
        }
        // A parameter marked @Nullable is a browse-without-an-account endpoint:
        // hand it null and let the handler decide what an anonymous caller sees.
        // Everything else still fails closed with a 401.
        if (parameter.isOptional()) return null;
        throw new ApiException(HttpStatus.UNAUTHORIZED, "unauthenticated", "Sign in to continue");
    }
}
