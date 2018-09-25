package com.mymvc.mvc.demo.action;

import java.io.IOException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.mymvc.mvc.annotation.JTAutowired;
import com.mymvc.mvc.annotation.JTController;
import com.mymvc.mvc.annotation.JTRequestMapping;
import com.mymvc.mvc.annotation.JTRequestParam;
import com.mymvc.mvc.demo.service.IDemoService;

@JTController
@JTRequestMapping("/demo")
public class DemoAction {
	@JTAutowired 
	private IDemoService demoService;
	@JTRequestMapping("/query.json")
	public void query(HttpServletRequest req,HttpServletResponse res,
	@JTRequestParam("name") String name) {
		String result=demoService.get(name);
		try {
			res.getWriter().write(result);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
}
