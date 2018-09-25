package com.mymvc.mvc.demo.service.serviceimpl;

import com.mymvc.mvc.annotation.JTService;
import com.mymvc.mvc.demo.service.IDemoService;
@JTService("tom")
public class IDemoServiceImpl implements IDemoService{
	public String get(String name) {
		return "My name is"+name;
	}
}
