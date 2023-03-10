package it.bz.idm.bdp.ninja.controller;

import javax.servlet.http.HttpServletRequest;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;

@Controller
public class CustomErrorController implements ErrorController {

	@RequestMapping("/error")
	@ResponseBody
	public String error(HttpServletRequest request) {
		throw new ResponseStatusException(
			HttpStatus.NOT_FOUND,
			"Route does not exist"
		);
	}
}
