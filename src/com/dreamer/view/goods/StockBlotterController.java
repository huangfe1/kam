package com.dreamer.view.goods;

import com.dreamer.domain.goods.StockBlotter;
import com.dreamer.domain.user.Admin;
import com.dreamer.repository.goods.GoodsDAO;
import com.dreamer.repository.goods.StockBlotterDAO;
import com.dreamer.service.goods.GoodsHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import ps.mx.otter.utils.WebUtil;
import ps.mx.otter.utils.message.Message;

import javax.servlet.http.HttpServletRequest;
import java.util.Optional;

@RestController
@RequestMapping("/stock")
public class StockBlotterController {

	
	@RequestMapping(value={"/edit.json"},method=RequestMethod.POST)
	public Message edit_enter(@ModelAttribute("stockBlotter")StockBlotter stock,Model model,boolean add,HttpServletRequest request){
		try{
			Admin user=(Admin)WebUtil.getCurrentUser(request);
			stock.setUser(user);
			if(!add){
				stock.setChange(-stock.getChange());
			}
			goodsHandler.addStock(stock);
			return Message.createSuccessMessage("新增库存成功");
		}catch(Exception exp){
			exp.printStackTrace();
			return Message.createFailedMessage(exp.getMessage());
		}
	}
	
	
	@ModelAttribute("stockBlotter")
	public StockBlotter preprocess(
			@RequestParam("id") Optional<Integer> id) {
		
		StockBlotter stockBlotter = null;
		if (id.isPresent()) {
			stockBlotter = (StockBlotter) stockBlotterDAO.findById(id.get());
		} else {
			stockBlotter = new StockBlotter();
		}
		return stockBlotter;
	}

	@Autowired
	private StockBlotterDAO stockBlotterDAO;
	@Autowired
	private GoodsDAO goodsDAO;
	@Autowired
	private GoodsHandler goodsHandler;
}
