package com.dreamer.service.goods;import com.dreamer.domain.account.GoodsAccount;import com.dreamer.domain.goods.Goods;import com.dreamer.domain.goods.Transfer;import com.dreamer.domain.goods.TransferApplyOrigin;import com.dreamer.domain.user.Agent;import com.dreamer.domain.user.User;import com.dreamer.repository.goods.DeliveryItemDAO;import com.dreamer.repository.goods.GoodsDAO;import com.dreamer.repository.goods.TransferDAO;import com.dreamer.repository.user.AgentDAO;import com.dreamer.repository.user.MutedUserDAO;import org.slf4j.Logger;import org.slf4j.LoggerFactory;import org.springframework.beans.factory.annotation.Autowired;import org.springframework.stereotype.Service;import org.springframework.transaction.annotation.Transactional;import ps.mx.otter.exception.BalanceNotEnoughException;import ps.mx.otter.exception.DataNotFoundException;import java.util.Objects;@Servicepublic class TransferHandler {    /**     * 根据用户的类型获取代理     * @param user     * @return     */    private Agent getAgentFromUser(User user){        if(user.isMutedUser()||user.isAdmin()){//如果转货人是公司           return  mutedUserDAO.loadFirstOne();        }else{//是代理          return agentDAO.findById(user.getId());        }    }    /**     * 退货申请     * @param transfer     * @param goodsIds     * @param quantitys     * @param user     * @param isBack     * @return     */    @Transactional    public Transfer applyBackTransfer(Transfer transfer, Integer[] goodsIds,Integer[] quantitys,User user) {        transfer.clear();        //查找出转货物人        Agent fromAgent=getAgentFromUser(user);        transfer.setUserByFromAgent(fromAgent);        //只有大区才能退货且只能退给公司        transfer.setApplyOrigin(TransferApplyOrigin.BACK);//退回上级        transfer.setUserByToAgent(agentDAO.findById(3));        try{            this.buildAndCalulateItemsPrice(transfer, goodsIds, quantitys);        }catch(BalanceNotEnoughException bne){            throw new BalanceNotEnoughException("您要退的货物 "+bne.getMessage()+",请核实后在退货");        }        transfer.apply();        Transfer result=transferDAO.merge(transfer);        LOG.debug("申请退货成功");        return result;    }	@Transactional	public Transfer applyTransfer(Transfer transfer, Integer[] goodsIds,			Integer[] quantitys) {		transfer.clear();		try{			this.buildAndCalulateItemsPrice(transfer, goodsIds, quantitys);		}catch(BalanceNotEnoughException bne){            if(!transfer.isBackedTransfer())			throw new BalanceNotEnoughException("你的上级 "+bne.getMessage()+",请通知及时补货");            throw new BalanceNotEnoughException("您要退的货物 "+bne.getMessage()+",请核实后在退货");		}		transfer.apply();		Transfer result=transferDAO.merge(transfer);		LOG.debug("申请转货成功");		return result;	}	@Transactional	public Transfer confirmByAdvance(Transfer transfer, Integer[] goodsIds,								  Integer[] quantitys) {		transfer.clear();		try{			this.buildAndCalulateItemsPrice(transfer, goodsIds, quantitys);		}catch(BalanceNotEnoughException bne){			throw new BalanceNotEnoughException("你的上级 "+bne.getMessage()+",请通知及时补货");		}		transfer.apply();//申请        transfer.confirmAutoByAdvance();//提交		Transfer result=transferDAO.merge(transfer);		LOG.debug("申请转货成功");		return result;	}		@Transactional	public Transfer calculate(Transfer transfer, Integer[] goodsIds,			Integer[] quantitys) {		transfer.clear();		try{			this.buildAndCalulateItemsPrice(transfer, goodsIds, quantitys);		}catch(BalanceNotEnoughException bne){			throw new BalanceNotEnoughException("你的上级 "+bne.getMessage()+",请通知及时补货");		}        transfer.calculate();		LOG.debug("申请转货价格核算成功");		return transfer;	}    /**     *     * @param transfer     * @param goodsIds     * @param quantitys     */	public void buildAndCalulateItemsPrice(Transfer transfer,Integer[] goodsIds,Integer[] quantitys){        		Goods[] goodses=new Goods[goodsIds.length];		for (int index = 0; index < goodsIds.length; index++) {			goodses[index] = goodsDAO.findById(goodsIds[index]);		}        buildAndCalulateItemsPrice(transfer,goodses,quantitys);	}    /**     * 计算把每个货物组成一个订单,并且计算每个货物的价格,并且验证转货物条件     * @param transfer     * @param goodsIds     * @param quantitys     */    private void buildAndCalulateItemsPrice(Transfer transfer,Goods[] goodses,Integer[] quantitys){        Agent fromAgent = transfer.getUserByFromAgent();        Agent toAgent = (Agent) transfer.getUserByToAgent();        for (int index = 0; index < goodses.length; index++) {            Goods goods =goodses[index];            Integer quantity=quantitys[index];            if(transfer.isBackedTransfer()||transfer.isOutTransfer()){//转货物或者退货,都不需要验证等级                transferValidate(toAgent, fromAgent, goods, quantity,true);            }            Agent temAgent =toAgent;            if(toAgent.isMutedUser()){//如果是退货的话,或者主动转出给上级的话                temAgent=fromAgent;            }else {//不是退货还要限制阀值                agentLevelTradingLimitedHandler.checkTradingLimit(fromAgent, goods, quantity);            }            transfer.addTransferItem(goods, quantity, temAgent                    .caculatePrice(goods, quantity));        }    }	@Transactional	public void confirmTransfer(Transfer transfer) {		if (transfer.isNew()) {			Agent fromAgent=transfer.getUserByFromAgent();			transfer.getItems().entrySet().stream().forEach(e->{				Goods goods=goodsDAO.findById(e.getKey());				Integer quantity=e.getValue().getQuantity();				agentLevelTradingLimitedHandler.checkTradingLimit(fromAgent, goods, quantity);			});			transfer.confirm();			transferDAO.merge(transfer);		}	}    /**     * 退回货物确认  hf     * @param transfer     */    @Transactional    public void confirmBackTransfer(Transfer transfer) {        if (transfer.isNew()) {            transfer.transferBackToParent();//退给上级            transferDAO.merge(transfer);        }    }	@Transactional	public void refuseTransfer(Transfer transfer) {		if (transfer.isNew()) {			transfer.refuse();			transferDAO.merge(transfer);		}	}	@Transactional	public void removeTransfer(Transfer transfer) {		if (transfer.isNew()) {			transferDAO.delete(transfer);		}	}    /**     * 主动转出     * @param transfer     * @param goodsIds     * @param quantitys     */    @Transactional	public void transferTo(Transfer transfer,			 Integer[] goodsIds, Integer[] quantitys,User user) {        transfer.setApplyOrigin(TransferApplyOrigin.OUT);//主动转出        Agent fromAgent=getAgentFromUser(user);//		Goods[] goodses=new Goods[goodsIds.length];//		for (int index = 0; index < goodsIds.length; index++) {//			goodses[index] = goodsDAO.findById(goodsIds[index]);//		}//		transferTo(transfer,goodses,quantitys);// 主动转出        String toAgentCode=transfer.getUserByToAgent().getAgentCode();        String toAgentName=transfer.getUserByToAgent().getRealName();//        Agent fromAgent = transfer.getUserByFromAgent();        Agent toAgent = agentDAO.findByAgentCode(toAgentCode);        if(toAgentCode.equals("01"))toAgent=mutedUserDAO.loadFirstOne();        if(Objects.isNull(toAgent)){            throw new DataNotFoundException("代理编号对应的代理不存在");        }        if (!Objects.equals(toAgent.getRealName(), toAgentName)) {            throw new DataNotFoundException("代理姓名错误");        }//		if(toAgent.notParentChildRelation(fromAgent) && !fromAgent.isMutedUser()){//			throw new DataNotFoundException("代理只能上下级之间主动转货");//		}//        for (int index = 0; index < goodses.length; index++) {//            transferValidate(toAgent, fromAgent, goodses[index], quantitys[index],transfer.isBackedTransfer());//            Agent temAgent =toAgent;//            if(transfer.getApplyOrigin()!= TransferApplyOrigin.OUT){//主动转出不需要验证等级//                agentLevelTradingLimitedHandler.checkTradingLimit(fromAgent, goodses[index], quantitys[index]);//            }else {//主动转出的//                if(toAgent.isMutedUser()){//如果上级是公司,计算自己价格//                    temAgent=fromAgent;//                }//                transfer.addTransferItem(goodses[index], quantitys[index], temAgent//                        .caculatePrice(goodses[index],quantitys[index]));//            }//        }        transfer.setUserByFromAgent(fromAgent);        transfer.setUserByToAgent(toAgent);        buildAndCalulateItemsPrice(transfer,goodsIds,quantitys);        transfer.transferToAnother();        transferDAO.merge(transfer);	}	//	@Transactional//	public void transferBack(Transfer transfer, String toAgentCode,//			String toAgentName, Integer[] goodsIds, Integer[] quantitys) {//		Goods[] goodses=new Goods[goodsIds.length];//		for (int index = 0; index < goodsIds.length; index++) {//			goodses[index] = goodsDAO.findById(goodsIds[index]);//		}//		transferBack(transfer,toAgentCode,toAgentName,goodses,quantitys);//	}	//	@Transactional//	public void transferBack(Transfer transfer, String toAgentCode,//			String toAgentName, Goods[] goodses, Integer[] quantitys) {////		Agent fromAgent = transfer.getUserByFromAgent();//		Agent toAgent = agentDAO.findByAgentCode(toAgentCode);//		if (Objects.isNull(toAgent)) {//			toAgent=mutedUserDAO.loadFirstOne();//			if(Objects.isNull(toAgent)){//				throw new DataNotFoundException("代理编号对应的代理不存在");//			}//		}////		if (!Objects.equals(toAgent.getRealName(), toAgentName)) {//			throw new DataNotFoundException("代理姓名错误");//		}//////		if(!fromAgent.isMyParent(toAgent)||toAgent.isMutedUser()){//转给公司////			throw new DataNotFoundException("代理只能向上级返货");////		}//		for (int index = 0; index < goodses.length; index++) {//			transferBackValidate(toAgent, fromAgent, goodses[index], quantitys[index]);//			transfer.addTransferItem(goodses[index], quantitys[index], fromAgent//					.caculatePrice(goodses[index]));//		}//        transfer.setUserByToAgent(toAgent);//		transfer.transferBackToParent();//		transferDAO.merge(transfer);//	}    /**     * 直接发货的时候主动转出     * @param transfer     * @param goodses     * @param quantitys     */	public void transferTo(Transfer transfer, Goods[] goodses, Integer[] quantitys) {        String toAgentCode=transfer.getUserByToAgent().getAgentCode();        String toAgentName=transfer.getUserByToAgent().getRealName();		Agent fromAgent = transfer.getUserByFromAgent();		Agent toAgent = agentDAO.findByAgentCode(toAgentCode);			if(Objects.isNull(toAgent)){				throw new DataNotFoundException("代理编号对应的代理不存在");			}		if (!Objects.equals(toAgent.getRealName(), toAgentName)) {			throw new DataNotFoundException("代理姓名错误");		}		//		if(toAgent.notParentChildRelation(fromAgent) && !fromAgent.isMutedUser()){//			throw new DataNotFoundException("代理只能上下级之间主动转货");//		}//		for (int index = 0; index < goodses.length; index++) {//			transferValidate(toAgent, fromAgent, goodses[index], quantitys[index],transfer.isBackedTransfer());//            Agent temAgent =toAgent;//            if(transfer.getApplyOrigin()!= TransferApplyOrigin.OUT){//主动转出不需要验证等级//                agentLevelTradingLimitedHandler.checkTradingLimit(fromAgent, goodses[index], quantitys[index]);//            }else {//主动转出的//                if(toAgent.isMutedUser()){//如果上级是公司,计算自己价格//                    temAgent=fromAgent;//                }//                transfer.addTransferItem(goodses[index], quantitys[index], temAgent//                        .caculatePrice(goodses[index],quantitys[index]));//            }//		}        transfer.setUserByFromAgent(fromAgent);        transfer.setUserByToAgent(toAgent);        buildAndCalulateItemsPrice(transfer,goodses,quantitys);		transfer.transferToAnother();		transferDAO.merge(transfer);	}    /**     * 转货物限制,授权限制(是否有授权),等级之间的限制(低等级是否能转给高等级),库存限制(库存是否够)     * @param toAgent     * @param fromAgent     * @param goods     * @param quantity     * @param isBack     */	private void transferValidate(Agent toAgent, Agent fromAgent, Goods goods, Integer quantity,boolean isBack) {		if (!toAgent.isActivedAuthorizedGoods(goods)) {			// throw new NotAuthorizationException("接收方对该产品没有授权");		}		GoodsAccount toGac=toAgent.loadAccountForGoodsNotNull(goods);		if (toGac == null) {			toGac=toAgent.addGoodsAccount(goods);			// throw new DataNotFoundException("接收方产品账户不存在");		}		GoodsAccount fromGac=fromAgent.loadAccountForGoodsNotNull(goods);		if (fromAgent.isAgent() && fromGac == null) {			fromGac=fromAgent.addGoodsAccount(goods);			/*if (fromAgent.isMutedUser()) {				fromAgent.addGoodsAccount(goods);			} else {				throw new DataNotFoundException("转出方产品账户不存在");			}*/		}		if (!isBack){//如果不是退货,就要限制上下级等级			agentLevelTradingLimitedHandler.checkAgentLevel(fromAgent, toAgent, goods);//转出方产品等级不能低于转入方等级		}		agentLevelTradingLimitedHandler.checkBalance(fromAgent, goods, quantity);		//agentLevelTradingLimitedHandler.checkTradingLimit(fromAgent, goods, quantity);	}		public void transferBackValidate(Agent toAgent, Agent fromAgent, Goods goods, Integer quantity) {		if (!toAgent.isActivedAuthorizedGoods(goods)) {			// throw new NotAuthorizationException("接收方对该产品没有授权");		}		GoodsAccount toGac=toAgent.loadAccountForGoodsNotNull(goods);		if (toGac == null) {			toGac=toAgent.addGoodsAccount(goods);			// throw new DataNotFoundException("接收方产品账户不存在");		}		GoodsAccount fromGac=fromAgent.loadAccountForGoodsNotNull(goods);		if (fromAgent.isAgent() && fromGac == null) {			fromGac=fromAgent.addGoodsAccount(goods);			/*if (fromAgent.isMutedUser()) {				fromAgent.addGoodsAccount(goods);			} else {				throw new DataNotFoundException("转出方产品账户不存在");			}*/		}		//agentLevelTradingLimitedHandler.checkAgentLevel(fromAgent, toAgent, goods);		agentLevelTradingLimitedHandler.checkBalance(fromAgent, goods, quantity);		//agentLevelTradingLimitedHandler.checkTradingLimit(fromAgent, goods, quantity);	}					@Autowired	private TransferDAO transferDAO;	@Autowired	private GoodsDAO goodsDAO;	@Autowired	private AgentDAO agentDAO;	@Autowired	private DeliveryItemDAO deliveryItemDAO;	@Autowired	private MutedUserDAO mutedUserDAO;		@Autowired	private AgentLevelTradingLimitedHandler agentLevelTradingLimitedHandler;	private final Logger LOG = LoggerFactory.getLogger(getClass());}