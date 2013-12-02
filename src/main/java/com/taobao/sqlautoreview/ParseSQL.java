/**
 * test
 * (C) 2011-2012 Alibaba Group Holding Limited.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * version 2 as published by the Free Software Foundation.
 * 
 *
 * Authors:
 *   danchen <danchen@taobao.com>
 *
 */


package com.taobao.sqlautoreview;

import java.util.HashMap;
import java.util.Map;
import java.util.Stack;
import java.util.regex.*;

import org.apache.log4j.Logger;



/*
 * function:ֻ�������parse
 */

public class ParseSQL {
	 //log4j��־
    private static Logger logger = Logger.getLogger(ParseSQL.class);
	
	//ԭʼ��SQL���
	public String sql;
	//SQL���� insert 0,update 1,delete 2,��ͨ select 3
	public int sql_type;
	//������Ϣ,���������Ϣ,��:�ָ�
	public String errmsg;
	//����
    public String tip;
	//����
	public String tablename;
	//�����
	public String alias_tablename;
	//where���������
	public Tree_Node whereNode;
	//��ѯ�ֶ�
    public String select_column;
    //��ѯ�������ֶ����Ķ�Ӧ��hash map
    public Map<String, String> map_columns;
    //group by�ֶ�
    public String groupbycolumn;
    //�����ֶ�
    public String orderbycolumn;
    //Muti-table sql statement tag
    public int tag;
	
	//���캯��
	public ParseSQL(String sql)
	{
		this.sql=sql.trim().toLowerCase();
		this.errmsg="";
		this.tip="";
		this.tablename="";
		this.groupbycolumn="";
		this.orderbycolumn="";
		//Ĭ������¶��ǵ����ѯ0:����;1:���
		this.tag=0;
		this.map_columns=new HashMap<String, String>();
	}
	
	//����һ��SQL������select,insert,update,delete
	public void sql_dispatch()
	{
		
		if(sql.substring(0, 6).equals("select")==true)
		{
			//����select
			parseSQLSelect();
			sql_type=3;
		}
		else if(sql.substring(0, 6).equals("insert")==true)
		{
		    //����insert
			parseSQLInsert();
			sql_type=0;
		}
		else if(sql.substring(0,6).equals("update")==true)
		{
			//����update
			parseSQLUpdate();
			sql_type=1;
		}
		else if(sql.substring(0, 6).equals("delete")==true)
		{
			//����delete
			parseSQLDelete();
			sql_type=2;
		}
		else
			//������SQL���
			sql_type=-1;
	}

	/*
	 * where�������ɵ����ĸ����,������or
	 * ���ȷʵ�������������,��ҪDBA����
	 */
	private void checkWhereTreeRootNode(Tree_Node treeRootNode)
	{
		if(treeRootNode==null)
		{
			this.errmsg="where tree root node is empty.";
			logger.warn(this.errmsg);
			return;
		}
		
		if(treeRootNode.node_content.equals("or")==true)
		{
			this.errmsg="where tree root node appears or key word,this is not allowed.";
			logger.error(this.errmsg);
		}
	}
	
	private void parseSQLDelete() {
		// TODO Auto-generated method stub
		// delete������SQL auto review,ֻ��Ҫ����where�����ֶμ���
		logger.info("SQL at parsing:"+this.sql);
		int i=0;
		int loop=0;
		//��һ���ƶ��±�
		if(i+6<sql.length() && sql.substring(0, 6).equals("delete")==true) 
			i=i+6;
		else
		{
			this.errmsg="not delete SQL statement.";
			return;
		}
		
		//�ڶ����ƶ��±�,����ո�
		while(i+1<sql.length() && sql.substring(i,i+1).equals(" ")==true)
			i++;
		
		//�������ƶ��±�,���from
		if(i+4<sql.length() && sql.substring(i, i+4).equals("from")==true)
			i=i+4;
		else
		{
			this.errmsg="not find from key word.";
			return;
		}
		
		//���Ĵ��ƶ��±�,����ո�
		while(i+1<sql.length() && sql.substring(i,i+1).equals(" ")==true)
			i++;
		
		//������ƶ��±�,���tablename
		while(i+1<sql.length() && sql.substring(i, i+1).equals(" ")==false)
		{
			tablename=tablename+sql.substring(i, i+1);
			i++;
		}
		
		logger.info("table name:"+this.tablename);
		
		//�������ƶ��±�,����ո�
		while(i+1<sql.length() && sql.substring(i,i+1).equals(" ")==true)
			i++;
		
		//���ߴ��ƶ��±�,���where�ֶ�
		if(i+5<=sql.length() && sql.substring(i, i+5).equals("where")==true)
			i=i+5;
		else
		{
			this.errmsg="not find where key word.";
			return;
		}
		
		//�쳣����
		if(i>sql.length()) {
			this.errmsg="not find where condition.";
			logger.warn(this.errmsg);
			return;
		}
		else
		{
			if(sql.substring(i).trim().length()==0)
			{
				this.errmsg="not find where condition.";
				logger.warn(this.errmsg);
				return;
			}
		}
		
		//�������������ֶ�
		whereNode = parseWhere(null,sql.substring(i).trim(),loop);
		
		//check whereNode tree
		checkWhereTreeRootNode(whereNode);
		
		logger.info("where condition:"+sql.substring(i));
	}

	private void parseSQLUpdate() {
		// TODO Auto-generated method stub
		// update������SQL auto review,ֻ��Ҫ������tablename,�Լ�where�����ֶμ���
		// update�����ܴ�����selectǶ�׵����
		logger.info("SQL at parsing:"+this.sql);
		int addr_where=0;
		int loop=0;
		tablename="";
		int i=0;
		
		//��һ���ƶ��±�
		if(i+6<sql.length() && sql.substring(0, 6).equals("update")==true) 
			i=i+6;
		else
		{
			this.errmsg="not update SQL statement.";
			return;
		}
		
		//�ڶ����ƶ��±�,����ո�
		while(i+1<sql.length() && sql.substring(i,i+1).equals(" ")==true)
			i++;
		
		//�������ƶ��±�,���tablename
		while(i+1<sql.length() && sql.substring(i, i+1).equals(" ")==false)
		{
			tablename=tablename+sql.substring(i, i+1);
			i++;
		}
		
		logger.info("table name:"+this.tablename);
		
		//check where key word
		addr_where=sql.indexOf(" where");
		if(addr_where<0)
		{
			this.errmsg="not find where key word.";
			logger.warn(this.errmsg);
			return;
		}
		
		//���һ����values���Ƿ�����sysdate()����,������������������һ��
		if(sql.indexOf("sysdate()",i)>0 && sql.indexOf("sysdate()",i)<addr_where){
			errmsg="use sysdate() function,this not allowed,you should use now() replace it.";
			logger.warn(errmsg);
			return;
		}
		
		if(addr_where+6>=sql.length())
		{
			this.errmsg="not find where condition.";
			logger.warn(this.errmsg);
			return;
		}
		whereNode=parseWhere(null, sql.substring(addr_where+6), loop);
		
		//check whereNode tree
		checkWhereTreeRootNode(whereNode);
		
		logger.info("where condition:"+sql.substring(addr_where+5));
	}

	/*
	 * ����ѯ�ֶ��Ƿ���Ϲ���
	 */
	private void selectColumnCheckValid(String columnsString) 
	{
		if(columnsString.equals("*")==true)
			tip="������select *,���ǲ�������,��д����ȷ��column name.";
	}
	
	/*
	 * ȡ����һ������,�������ʺ�Ŀո��ֹͣ
	 */
    public String getNextToken(String str,int from_addr)
    {
    	String token="";
    	//������ȫ���
    	if(str==null || str.length()<from_addr){
    		return null;
    	}
    	//�ո�
    	while(from_addr<str.length() && str.substring(from_addr, from_addr+1).equals(" "))
    	{
    		from_addr++;
    	}
    	//����˳�����
    	if(from_addr>str.length()){
    		return null;
    	}
    	//token
    	while(from_addr<str.length() && str.substring(from_addr, from_addr+1).equals(" ")==false)
    	{
    		token=token+str.substring(from_addr, from_addr+1);
    		from_addr++;
    	}
    	
    	return token;
    }
	/*
	 * �Ը��ֵ�ǰ֧�ֵ�select SQL�����һ���ַ�����
	 */
	private void parseSQLSelect()
	{
		//where word check
		if(sql.indexOf(" where ")<0){
			this.errmsg="sql ������where�ؼ���,��ҪDBA����";
			return;
		}
		//and word check
		if(getNextToken(sql, sql.indexOf(" where ")+7).equals("and"))
		{
			this.errmsg="where������һ��������and�ؼ���,����sqlmapfile";
			return;
		}
		//&gt; &lt;����SQLMAP�г�����һ�ִ���
		if(sql.indexOf("&gt;")>0 || sql.indexOf("&lt;")>0){
			this.errmsg="in sql-map file,sql where����е�>,<������Ҫ��<![CDATA[]]>ת��";
			return;
		}
		
		//join������ʽ�ݲ�֧��
		if(sql.indexOf(" join ")>0 && sql.indexOf(" on ")>0)
		{
			this.errmsg="join or left join or right join is not supported now.";
			return;
		}
		
		//��򵥵�SQL���
		if(sql.indexOf(".") < 0)
		{
			//���ܺ���in�Ӳ�ѯ,���ֻ��ж��select����
			if(sql.indexOf("select ", 7)>0)
			{
				this.errmsg="sub-query is not supported now.";
				return;
			}
			parseSQLSelectBase();
			return;
		}
		
		//��ҳ��������
		if(sql.indexOf("order by") > 0 && sql.indexOf("limit ") > 0)
		{
			//�ټ��limit #start#,#end#�﷨
			int addr=sql.indexOf("limit ");
			addr=addr+6;
			if(sql.indexOf(",", addr) > sql.indexOf("limit "))
			{
				parseSQLSelectPage();
				return;
			}
		}
		
		//ʹ����.,���Ҵ��ڶ��select,����Ҳ��֧��
		if(sql.indexOf("select ", 7)>0)
		{
			this.errmsg="�������͵Ķ��select�Ĳ�ѯ�ݲ�֧�� .";
			return;
		}
		
		//�����ǲ��ǵ����ѯʹ���˱���
		int is_mutiple_table=checkMutipleTable(sql);
		//����ʹ���˱���
		if(is_mutiple_table==1){
			parseSQLSelectBase();
			return;
		}
		//������
		if(is_mutiple_table==2){
			this.tag=1;
			return;
		}
		
		this.errmsg="��ǰSQL��ʽ,����֧��";
		return;
	}
	
	//�����ѯ�Ƿ�ʹ���˱���
	//��Ҫ��from�ؼ��ֺ���
	//from tablename t where
	//����ֵ-1�д���
	//����ֵ0,����δʹ�ñ���
	//����ֵ1,����ʹ�ñ���
	//����ֵ2,���
	private int checkMutipleTable(String sql) {
		// TODO Auto-generated method stub
		int addr;
		int length=sql.length();
		int i;
		int start;
		boolean is_find_as=false;
		String alias_name="";
		addr=sql.indexOf(" from ");
		if(addr<0) return -1;
		i=addr+6;
		//space
		while(i+1<length && sql.substring(i, i+1).equals(" ")==true)
			i++;
		//table name
		while(i+1<length && sql.substring(i, i+1).equals(" ")==false)
			i++;
		//space
		while(i+1<length && sql.substring(i, i+1).equals(" ")==true)
			i++;
		//�����û��tablename as t1���������﷨
		if(i+3<sql.length() && sql.substring(i, i+3).equals("as ")==true){
			i=i+3;
			is_find_as=true;
		}
		//token=where?
		start=i;
		while(i+1<length 
				&& sql.substring(i, i+1).equals(" ")==false
				&& sql.substring(i,i+1).equals(",")==false)
			i++;
		alias_name=sql.substring(start, i).trim();
		if(alias_name.equals("where")==true){
			return 0;
		}
		else 
		{
			//�жϵ�һ���ǿ��ַ��Ƿ�Ϊ,��
			while(i+1<length && sql.substring(i, i+1).equals(" ")==true)
				i++;
			if(sql.substring(i, i+1).equals(",")==true)
			{
				logger.info("mutiple tables,this is not support now.");
				return 2;
			}
			//����ʹ���˱���,����Ҫ�����滻
			logger.info("alias name:"+alias_name);
			this.sql=this.sql.replace(" "+alias_name+" ", " ");
			alias_name=alias_name+".";
			this.sql=this.sql.replace(alias_name, "");
			if(is_find_as==true){
				this.sql=this.sql.replace(" as ", " ");
			}
			
			return 1;
		}
		
	}

	/*
	 * select column_name,[column_name] from table_name where ���� order by column_name limit #endnum
	 * ������򵥵�select SQL��ѯ
	 */
	private void parseSQLSelectBase() {
		// TODO Auto-generated method stub
		int i=0,tmp=0;
		int addr_from;
		int addr_where;
		int addr_group_by;
		int addr_order_by;
		int addr_limit;
		String wherestr="";
		int loop=0;
		
		logger.info("SQL at parsing:"+sql);
		
		// ���select�ؼ���
		if(i+6<sql.length() && sql.substring(0, 6).equals("select")==true)
		{
			i=i+6;
		}
		else
		{
			this.errmsg="not select SQL statement.";
			return;
		}
		
		//�����ѯ�ֶΣ������Ϸ���
		addr_from=sql.indexOf(" from ");
		if(addr_from==-1)
		{
			this.errmsg="not find from key word.";
			return;
		}
		this.select_column=sql.substring(i, addr_from).trim();
		selectColumnCheckValid(this.select_column);
		//���������ı���ӳ��
		addToColumnHashMap(this.select_column,this.map_columns);
		
		logger.info("select columns:"+this.select_column);
		
		//����table name
		i=addr_from+6;
		addr_where=sql.indexOf(" where ", i);
		if(addr_where==-1)
		{
			this.errmsg="Select SQL����б������where�ؼ���,�������SQLȷʵ��Ҫ,��ҪDBA�������";
			return;
		}
		
		this.tablename=sql.substring(i, addr_where);
		
		logger.info("table name:"+this.tablename);
		
		//����where����
		i=addr_where+7;
		addr_group_by = sql.indexOf("group by");
		addr_order_by = sql.indexOf("order by");
		addr_limit = sql.indexOf("limit ");
		
		if(addr_group_by<0 && addr_order_by<0 && addr_limit<0)
		{
			wherestr = sql.substring(i);
		}
		else {
			for(tmp=i;tmp<sql.length()-8;tmp++)
			{
				if(sql.substring(tmp, tmp+8).equals("group by")== false 
						&& sql.substring(tmp,tmp+8).equals("order by")==false 
						&& sql.substring(tmp, tmp+6).equals("limit ")==false)
				wherestr=wherestr+sql.substring(tmp, tmp+1);
				else {
					break;
				}
			}
		}
		//����where string
		int wherestr_len=wherestr.length();
		wherestr=handleBetweenAnd(wherestr);
		this.whereNode=this.parseWhere(null,wherestr,loop);
		
		//check whereNode tree
		checkWhereTreeRootNode(whereNode);
		
		logger.info("where condition:"+wherestr);
		
		//������������,ֻ�ܼ���handleBetweenAnd��������֮ǰ��wherestr�ĳ���
		i=i+wherestr_len;
		if(i<sql.length())
		{
			if(sql.substring(i, i+8).equals("group by")==true)
			{
				//��������������ֶ�,Ҳ����order by���ֶ�,����������
				//��group by��ʱ��,����ͨ����having�ؼ���
				if(sql.indexOf("having", i+8) > 0)
				{
					this.groupbycolumn=sql.substring(i+8, sql.indexOf("having", i+7)).trim();
				}
				else if(sql.indexOf("order by", i+8)>0)
				{
					this.groupbycolumn=sql.substring(i+8, sql.indexOf("order by", i+8)).trim();
					
				}
				else if(sql.indexOf("limit",i+8)>0){
					this.groupbycolumn=sql.substring(i+8, sql.indexOf("limit", i+8)).trim();
				}
			}
		    
			logger.info("group by columns:"+this.groupbycolumn);
			
			if(sql.indexOf("order by",i) >= i)
			{
				if(sql.indexOf("limit ",i) > sql.indexOf("order by",i))
				{
					//����limit,��������������ֶ�,����limit��ֹ
					if(this.orderbycolumn.length()>0)
						this.orderbycolumn=this.orderbycolumn+","+sql.substring(sql.indexOf("order by")+8, sql.indexOf("limit"));
					else {
						this.orderbycolumn=sql.substring(sql.indexOf("order by",i)+8, sql.indexOf("limit "));
					}	
				}
				else {
					//������limit,��ֱ�ӵ�ĩβ
					if(this.orderbycolumn.length()>0)
						this.orderbycolumn=this.orderbycolumn+","+sql.substring(sql.indexOf("order by",i)+8);
					else {
						this.orderbycolumn=sql.substring(sql.indexOf("order by",i)+8);
					}
				}
				
				this.orderbycolumn=this.orderbycolumn.replace(" asc", " ");
				this.orderbycolumn=this.orderbycolumn.replace(" desc", " ");
			}
			
			this.orderbycolumn=this.orderbycolumn.replace(" ", "");
			logger.info("order by columns:"+this.orderbycolumn);
		}
	}

	/*
	 * ���������ı���ӳ��
	 * column as alias_column,column as alias_column
	 * or
	 * function(column) as alias_column
	 * ʾ��:
	 * SELECT CONCAT(last_name,', ',first_name) AS full_name FROM mytable ORDER BY full_name;
	 * ��Ϊselect_expr��������ʱ��AS�ؼ�������ѡ�ġ�ǰ������ӿ���������д��
     * SELECT CONCAT(last_name,', ',first_name) full_name FROM mytable ORDER BY full_name;
	 */
	public static void addToColumnHashMap(String select_exprs,
			Map<String, String> map) 
	{
		//�����ж�
		if(select_exprs==null){
			return;
		}
		select_exprs=select_exprs.toLowerCase();
		logger.debug("addToColumnHashMap select_exprs:"+select_exprs);
		//�ȴ�����򵥵����
		if(select_exprs.indexOf("(")<0)
		{
			String[] array_columns=select_exprs.split(",");
			for(int i=0;i<array_columns.length;i++)
			{
				dealSingleSelectExpr(array_columns[i],map);
			}
			return;
		}
		
		//ʹ���˺���,���������ŵ����,����
		int i=0;
		int start=0;
		int addr_douhao=0;
		int douhao_before_left_kuohao;
		int douhao_before_right_kuohao;
		String select_expr;
		while(i<select_exprs.length())
		{
			addr_douhao=select_exprs.indexOf(",",i);
			if(addr_douhao<0){
				//���һ��select_expr
				select_expr=select_exprs.substring(start);
		    	dealSingleSelectExpr(select_expr, map);
				break;
			}
			//�����������Ƿ�����ȷ�Ķ���,�����Ǻ�������ʹ�õĶ���
			douhao_before_left_kuohao=getWordCountInStr(select_exprs,"(",addr_douhao);
			douhao_before_right_kuohao=getWordCountInStr(select_exprs,")",addr_douhao);
		    if(douhao_before_left_kuohao==douhao_before_right_kuohao){
		    	//����һ��������select_expr
		    	select_expr=select_exprs.substring(start, addr_douhao);
		    	dealSingleSelectExpr(select_expr, map);
		    	start=addr_douhao+1;
		    	i=start;
		    }else {
				//���Ǻ��������,Ѱ����һ������
		    	i=addr_douhao+1;
			}
		}
	}
	
	/*
	 * ͳ��һ�����ų��ֵĴ���
	 */
	private static int getWordCountInStr(String str,String symbol,int addr_douhao)
	{
		int count=0;
		if(str==null || symbol==null ||str.length()<=addr_douhao){
			return -1;
		}
		for(int i=0;i<addr_douhao;i++)
		{
			if(str.substring(i, i+1).equals(symbol)){
				count++;
			}
		}
	
		return count;
	}
	
	/*
	 * ��������select_expr
	 * column as alias_column
	 * or
	 * function(column) as alias_column
	 */
	private static void dealSingleSelectExpr(String select_expr,Map<String, String> map)
	{
		String alias_column_name="";
		String column_name="";
		String word="";
		
		
		if(select_expr==null || select_expr.trim().equals("")){
			return;
		}
		
		logger.debug("dealSingleSelectExpr select_expr:"+select_expr);
		
		int k=select_expr.length();
		//��ñ���
		while(k-1>=0 && !select_expr.substring(k-1, k).equals(" "))
		{
			alias_column_name=select_expr.substring(k-1, k)+alias_column_name;
			k--;
		}
		if(k==0){
			//���������б���
			column_name=alias_column_name;
			map.put(alias_column_name, column_name);
			logger.debug("column_name:"+column_name+" alias_column_name:"+alias_column_name);
			return;
		}
		//����ո�
		while(k-1>=0 && select_expr.substring(k-1, k).equals(" "))
		{
			k--;
		}
		//����as,�������������ߺ�����
		while(k-1>=0 && !select_expr.substring(k-1, k).equals(" "))
		{
			word=select_expr.substring(k-1, k)+word;
			k--;
		}
		
		if(!word.equals("as"))
		{
			column_name=word;
			logger.debug("column_name:"+column_name+" alias_column_name:"+alias_column_name);
			map.put(alias_column_name,column_name);
			return;
		}
		
		//����ո�
		while(k-1>=0 && select_expr.substring(k-1, k).equals(" "))
		{
			k--;
		}
		
		//���������ߺ�����
		column_name=select_expr.substring(0, k);
		logger.debug("column_name:"+column_name+" alias_column_name:"+alias_column_name);
		map.put(alias_column_name,column_name);	
	}

	/*
	 * ����where����е�between and �﷨
	 * 
	 */
	public String handleBetweenAnd(String wherestr) 
	{
		String tmp_wherestr=wherestr;
		String resultString="";
		String column_name;
		int start=0;
		String matchString;
		int addr,len;
		
		if(tmp_wherestr.indexOf(" between ") < 0)
		{
			resultString = tmp_wherestr;
		}
		else 
		{
			//��between #value# and�м�#value#�еĿո�Ҫȥ��
			tmp_wherestr=removeSpace(tmp_wherestr);
			Pattern pattern = Pattern.compile("\\s+[a-zA-Z][0-9_a-zA-Z\\.]+\\s+between\\s+[',:#+\\-0-9_a-zA-Z\\(\\)]+\\sand\\s+");
			Matcher matcher = pattern.matcher(tmp_wherestr);
			while(matcher.find())
			{
				matchString=matcher.group();
				len=matchString.length();
				addr=tmp_wherestr.indexOf(matchString);
				column_name = matchString.trim().substring(0, matchString.trim().indexOf(" "));
				//��between�滻��>=����
				matchString=matchString.replace(" between ", " >= ");
				//��and����Ŀո񴦲���<=����
				matchString=matchString+column_name+" <= ";
				//���쵱ǰ��resultString
				resultString=resultString+tmp_wherestr.substring(start,addr)+matchString;
				//�����´ο�ʼ��startλ��
				start=addr+len;
			}//end while
			
			//��ȫ�����SQL
			if(start < tmp_wherestr.length())
			{
				resultString=resultString+tmp_wherestr.substring(start);
			}
			
		}
		
		return resultString;
	}

	
	/*
	 * ��between #value# and�м�#value#�еĿո�Ҫȥ��,��#����
	 */
	public String removeSpace(String tmp_wherestr) {
		String tmpString="";
		int addr_between=tmp_wherestr.indexOf(" between ");
		int addr_and;
		int start=0;
		while(addr_between > -1)
		{
			addr_and = tmp_wherestr.indexOf(" and ", addr_between);
			tmpString=tmpString+tmp_wherestr.substring(start, addr_between)+" between "+tmp_wherestr.substring(addr_between+9, addr_and).trim().replaceAll(" ", "#")+" and ";
			addr_between=tmp_wherestr.indexOf(" between ",addr_and+5);
			start= addr_and+5;
		}
		if(start<tmp_wherestr.length())
		{
			tmpString=tmpString+tmp_wherestr.substring(start);
		}
		return tmpString;	
	}

	/*
	 * �Ա�mysql�����ҳ�Ĺ淶
	 * ���շ�ҳ�Ĺ�����ƥ��,�������ҳ�Ľ���
	 * ʾ��1
	 * root@test 09:44:03>explain select t1.id, t1.manager_nick, t1.gmt_create, t1.nick
		-> from (select id
		-> from test
		-> where manager = ''
		-> and is_open = 2
		-> order by gmt_create limit 1, 10) t2 straight_join test t1
		-> where t1.id=t2.id
	 * ʾ��2
	 * root@test 09:45:28>explain select t1.id, t1.manager_nick, t1.gmt_create, t1.nick
		-> from (select id
		-> from test
		-> where manager = ''
		-> and is_open = 2
		-> order by gmt_create limit 1, 10) t2 ,test t1 force index(primary)
		-> where t1.id=t2.id;
	 */
	private void parseSQLSelectPage()
	{
		logger.info("SQL at parsing:"+this.sql);
		int i=0;
		int addr_from;
		String subsqlString;
		String alias_left_table;
		String alias_right_table;
		String real_tablename;
		if(i+6<sql.length() && sql.substring(0, 6).equals("select")==true)
		{
			i=i+6;
		}
		else
		{
			this.errmsg="not select SQL statement.";
			return;
		}
		addr_from=sql.indexOf(" from ");
		if(addr_from==-1)
		{
			this.errmsg="not find from key word.";
			return;
		}
		this.select_column=sql.substring(i, addr_from).trim();
		selectColumnCheckValid(this.select_column);
		if(this.errmsg.length()>0) return;
		
		i=addr_from+6;
		//����from�������������������,���������ݱ���������µĸ�ʽ
		//select primary key from table name where ���� order by column_name asc/desc limit #1,#2
		//��stack����ȡ���sub SQL��ѯ
		while(i+1<sql.length() && sql.substring(i,i+1).equals(" ")==true)
			i++;
		//�����ҵ�������
		if(i+1<sql.length() && sql.substring(i, i+1).equals("(")==false)
		{
			this.errmsg="û���ҵ���ҳ����������";
			return;
		}
		int start=i;
		Stack<String> stack = new Stack<String>();
		String tmp_s;
		while (i<sql.length()) {
			tmp_s=sql.substring(i, i+1);
			if(tmp_s.equals(")")==false)
				//������ѹջ
			    stack.push(tmp_s);
			else {
				//��ջ,ֱ������������
				while(stack.pop().equals("(")==false)
				{
					;
				}
				//�ж�ջ�Ƿ�Ϊ��,Ϊ��,�����ҵ���ȷλ��	
				if(stack.isEmpty()==true)
					break;
			}
			
			i++;
		}//end while
		subsqlString=sql.substring(start+1, i);
		
		
		//�������������
		i++;
		if(sql.indexOf(",", i) > 0)
		{
		    alias_left_table = sql.substring(i, sql.indexOf(",", i)).trim();
		    //����������
		    i= sql.indexOf(",", i)+1;
		    while(sql.substring(i,i+1).equals(" ")==true)
				i++;
		    real_tablename = sql.substring(i, sql.indexOf(" ", i));
		    //�����ı���
		    i= sql.indexOf(" ", i);
		    while(sql.substring(i,i+1).equals(" ")==true)
				i++;
		    alias_right_table = sql.substring(i, sql.indexOf(" ",i));
		    //���뺬��force index(primary)�ؼ���
		    //step 1: force
		    i=sql.indexOf(" ",i);
		    while(i+1<sql.length() && sql.substring(i,i+1).equals(" ")==true)
				i++;
		   
		    if(i+5<sql.length() && sql.substring(i, i+5).equals("force")==false)
		    {
		    	this.errmsg="not find force key word.";
		    	return;
		    }
		    //step 2:index
		    i=i+5;
		    while(i+1<sql.length() && sql.substring(i,i+1).equals(" ")==true)
				i++;
		    if(i+5<sql.length() && sql.substring(i, i+5).equals("index")==false)
		    {
		    	this.errmsg="not find force index key word.";
		    	return;
		    }
		    //step 3:(primary)
		    i=i+5;
		    while(i+1<sql.length() && sql.substring(i,i+1).equals(" ")==true)
				i++;
		    if(i+1<sql.length() && sql.substring(i, i+1).equals("(")==false)
		    {
		    	this.errmsg="not find force index( key word.";
		    	return;
		    }
		    i++;
		    while(i+1<sql.length() && sql.substring(i,i+1).equals(" ")==true)
				i++;
		    if(i+7<sql.length() && sql.substring(i, i+7).equals("primary")==false)
		    {
		    	this.errmsg="not find force index(primary key word.";
		    	return;
		    }
		    i=i+7;
		    while(i+1<sql.length() && sql.substring(i,i+1).equals(" ")==true)
				i++;
		    if(i+1<sql.length() && sql.substring(i, i+1).equals(")")==false)
		    {
		    	this.errmsg="not find force index(primary) key word.";
		    	return;
		    }
		    i++;
		}
		else {
			//��������һ�����ӷ�ʽstraight_join 
			if(sql.indexOf("straight_join", i) > 0)
			{
				alias_left_table=sql.substring(i, sql.indexOf("straight_join ", i)).trim();
				i=sql.indexOf("straight_join ", i)+14;
				while(i+1<sql.length() && sql.substring(i,i+1).equals(" ")==true)
						i++;
				real_tablename = sql.substring(i, sql.indexOf(" ", i)).trim();
			    //�����ı���
				i=sql.indexOf(" ", i);
			    while(i+1<sql.length() && sql.substring(i,i+1).equals(" ")==true)
					i++;
			    alias_right_table = sql.substring(i, sql.indexOf(" ",i)).trim();
			    i= sql.indexOf(" ",i);
			}
			else {
				this.errmsg ="cann't recongnize table join method.";
				return;
			}
		}
		
		//����where
		while(i+1<sql.length() && sql.substring(i,i+1).equals(" ")==true)
			i++;
		
		if(i+6<sql.length() && sql.substring(i, i+6).equals("where ")==false)
		{
			this.errmsg="not find where key word.";
	    	return;
		}
		//�����������
		i=i+6;
		while(i+1<sql.length() && sql.substring(i,i+1).equals(" ")==true)
			i++;
		if(sql.indexOf("=", i)==-1)
		{
			this.errmsg="��������û��ʹ��=��";
			return;
		}
		else {
			ParseSQL ps = new ParseSQL(subsqlString);
			ps.sql_dispatch();
			if(ps.tablename.equals(real_tablename)==false)
			{
				this.errmsg="�����ҳ,������һ��";
				return;
			}
			String str1=alias_left_table+"."+ps.select_column;
			String str2=alias_right_table+"."+ps.select_column;
			if(sql.indexOf(str1,i)==-1)
			{
				this.errmsg=this.errmsg+":û���ҵ�"+str1;
				return;
			}
			
			if(sql.indexOf(str2,i)==-1)
			{
				this.errmsg=this.errmsg+":û���ҵ�"+str2;
				return;
			}	
			
			//��������ߵ�����,˵����ȫƥ���ҳ�ı�׼д��
			this.tablename=ps.tablename;
			this.whereNode=ps.whereNode;
			this.orderbycolumn=ps.orderbycolumn;
		}
		
	}
	
	private void parseSQLInsert() 
	{
		// insert SQL
		logger.info(sql);
		int i=0;
		int addr_values;
		// ���insert�ؼ���
		if(sql.substring(0, 6).equals("insert")==true)
		{
			i=i+6;
		}
		else
		{
			errmsg="��insert���";
			return;
		}
		
		//�������Ĺؼ�����into
		while(sql.substring(i, i+1).equals(" ")==true)
		{
			i++;
		}
		if(sql.substring(i, i+4).equals("into")==false)
		{
			errmsg="insert sql���ȱ��into�ؼ���,�����﷨����";
			return;
		}
		else {
			i=i+4;
		}
		
		//�������������
		while(sql.substring(i, i+1).equals(" ")==true)
		{
			i++;
		}
		while(sql.substring(i, i+1).equals(" ")==false && sql.substring(i, i+1).equals("(")==false)
		{
			tablename=tablename+sql.substring(i, i+1);
			i++;
		}
		logger.info(tablename);
		//(col1,col2)values(#col1#,#col2#)
		addr_values=sql.indexOf("values",i);
		if(addr_values<0){
			errmsg="not find values key word.";
			logger.warn(errmsg);
			return;
		}
		
		//�����û��д����,����Ҫ��ȷд������,����Ϊ��
		int kuohao_left=sql.indexOf("(",i);
		int kuohao_right=sql.indexOf(")",i);
		if(kuohao_left>=i && kuohao_right > kuohao_left && kuohao_right < addr_values){
			;
		}else {
			errmsg="between tablename and values key word,you must write columns clearly.";
			logger.warn(errmsg);
			return;
		}
		
		//���һ����values���Ƿ�����sysdate()����,������������������һ��
		if(sql.indexOf("sysdate()",addr_values)>0){
			errmsg="use sysdate() function,this not allowed,you should use now() replace it.";
			logger.warn(errmsg);
			return;
		}
	}
	
	/*
	 * ��������ѻ����Ĳ���,����a=5 build��һ����
	 * �� parseBase()��������
	 */
	private Tree_Node buildTree(Tree_Node rootnode,String str,int addr,int offset)
	{
		Tree_Node node = new Tree_Node();
		Tree_Node left_child_node = new Tree_Node();
		Tree_Node right_child_node = new Tree_Node();
		
		//��ȡ�������
		node.node_content=str.substring(addr, addr+offset).trim();
		node.node_type=2;
		node.parent_node=rootnode;
		node.left_node=left_child_node;
		node.right_node=right_child_node;
		//����
		left_child_node.node_content=str.substring(0, addr).trim();
		left_child_node.node_type=1;
		left_child_node.parent_node=node;
		left_child_node.left_node=null;
		left_child_node.right_node=null;
		//�Һ���
		right_child_node.node_content=str.substring(addr+offset).trim();
		right_child_node.node_type=3;
		right_child_node.parent_node=node;
		right_child_node.left_node=null;
		right_child_node.right_node=null;
		
		return node;
	}
	/*
	 * ���������������,����a=5  ���� a>#abc#
	 */
	private Tree_Node parseBase(Tree_Node rootnode,String str)
	{
		int addr;
		
		addr=str.indexOf(">=");
		if(addr > 0) 
		{
			return buildTree(rootnode,str,addr,2);
		}
		
		addr=str.indexOf("<=");
		if(addr > 0) 
		{
			return buildTree(rootnode,str,addr,2);
		}
		
		addr=str.indexOf(">");
		if(addr > 0) 
		{
			return buildTree(rootnode,str,addr,1);
		}
		
		addr=str.indexOf("<");
		if(addr > 0) 
		{
			return buildTree(rootnode,str,addr,1);
		}
		
		addr=str.indexOf("!=");
		if(addr > 0) 
		{
			return buildTree(rootnode,str,addr,2);
		}
		
		addr=str.indexOf("=");
		if(addr > 0) 
		{
			return buildTree(rootnode,str,addr,1);
		}
		
		addr=str.indexOf(" in ");
		if(addr > 0) 
		{
			//�����Ϊin,��Ҫ��������,�ⲿ�ݴ�����Ҫ����
			//������ܺ����Ӳ�ѯ
			return buildTree(rootnode,str,addr,4);
		}
		
		addr=str.indexOf(" like ");
		if(addr > 0) 
		{
			return buildTree(rootnode,str,addr,6);
		}
		
		addr=str.indexOf(" is ");
		if(addr > 0) 
		{
			return buildTree(rootnode,str,addr,4);
		}
		
		return null;
	}
	
	public Tree_Node parseWhere(Tree_Node rootnode,String str_where,int loop)
	{
		//�ݹ���ȿ���
		loop++;
		if(loop>10000) return null;
		
		String str=str_where.trim();
		Tree_Node node=new Tree_Node();
		int addr_and;
		int addr_or;
		//����Ƿ��������ų���,���������ڵı��ʽ���еݹ�
		if(str.substring(0, 1).equals("(")==true){
			    //���ҵ������ԳƵ������ŵ�λ��
				//SQL����к���in�ؼ��ֶ�,��Ҫ��������
				Stack<String> stack = new Stack<String>();
				int k=0;
				String tmp_s;
				while (k<str.length()) {
					tmp_s=str.substring(k, k+1);
					if(tmp_s.equals(")")==false)
						//������ѹջ
					    stack.push(tmp_s);
					else {
						//��ջ,ֱ������������
						while(stack.pop().equals("(")==false)
						{
							;
						}
						//�ж�ջ�Ƿ�Ϊ��,Ϊ��,�����ҵ���ȷλ��	
						if(stack.isEmpty()==true)
							break;
					}
					
					k++;
				}//end while
				
				if(k==str.length()-1)
				{
					//���Ҳ��ޱ��ʽ
					return parseWhere(rootnode,str.substring(1,k),loop);
				}
				else {
					//�Ҳ��б��ʽ,���ҵ���һ��and ���� or,������һ��
					if(str.substring(k+1, k+6).equals(" and ")==true)
					{
						node.node_content="and";
						node.node_type=4;
						node.left_node=parseWhere(node, str.substring(1,k), loop);
						node.right_node=parseWhere(node, str.substring(k+6), loop);
						node.parent_node=rootnode;
					}
					else if(str.substring(k+1, k+5).equals(" or ")==true)
					{
						node.node_content="or";
						node.node_type=4;
						node.left_node=parseWhere(node, str.substring(1,k), loop);
						node.right_node=parseWhere(node, str.substring(k+5), loop);
						node.parent_node=rootnode;
					}
					
					return node;
				    
				}
		}
		else 
		{
			addr_and = str.indexOf(" and ");
			addr_or = str.indexOf(" or ");
			if(addr_and > 0 && addr_or > 0)
				if(addr_and < addr_or)
				{
					//�����ҵ�and
					node.node_content="and";
			    	node.node_type=4;
			    	node.parent_node=rootnode;
			    	node.left_node=parseBase(node,str.substring(0,addr_and).trim());
			    	node.right_node=parseWhere(node,str.substring(addr_and+5),loop);
			    	return node;
				}
				else
				{
					//�����ҵ�or
					node.node_content="or";
				    node.node_type=4;
				    node.parent_node=rootnode;
				    node.left_node=parseBase(node,str.substring(0,addr_or).trim());
				    node.right_node=parseWhere(node,str.substring(addr_or+4),loop);
				    return node;
				}
			else if(addr_and > 0)
			{
				node.node_content="and";
		    	node.node_type=4;
		    	node.parent_node=rootnode;
		    	node.left_node=parseBase(node,str.substring(0,addr_and).trim());
		    	node.right_node=parseWhere(node,str.substring(addr_and+5),loop);
		    	return node;
			}
			
			else if(addr_or > 0)
			{
				node.node_content="or";
			    node.node_type=4;
			    node.parent_node=rootnode;
			    node.left_node=parseBase(node,str.substring(0,addr_or).trim());
			    node.right_node=parseWhere(node,str.substring(addr_or+4),loop);
			    return node;
			}
			else {
				//�����������
	    	    return parseBase(rootnode,str);
			}
		}   
	}
    
	/*
	 * ���һ��������Ϣ
	 */
	public void printTree(Tree_Node rootnode)
	{
		if(rootnode != null)
		{	
			System.out.println("NODE ID:"+rootnode.hashCode()+", NODE CONTENT:"+rootnode.node_content);
		}
		
		if(rootnode.left_node != null)
		{
			System.out.println("My PARENT NODE CONTENT:"+rootnode.node_content+", NODE ID:"+rootnode.hashCode()+", LEFT CHILD ");
		    printTree(rootnode.left_node);
		}
		
		if(rootnode.right_node != null)
		{
			System.out.println("My PARENT NODE CONTENT:"+rootnode.node_content+", NODE ID:"+rootnode.hashCode()+", RIGHT CHILD ");
			printTree(rootnode.right_node);
		}
			
	}
	
}
