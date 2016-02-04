//VoipPhone
//Created by Janis Narbuts
//Copyright (c) 2004-2012 Tivi LTD, www.tiviphone.com. All rights reserved.

#ifndef _C_TIVI_SIP_H
#define _C_TIVI_SIP_H

#include "sip.h"
#include <string.h>
#include <stdlib.h>
#include <stdio.h>


class CSip{
   DSTR arrStrList[MAX_TOKEN_COUNT+5];
   
public:
   CSip()
      :gspSIPMsg(NULL),
      pContent(NULL),strList(NULL),iSipHasFirstLine(0),dropPacket(0),
      n(0),parsed(0),tokensParsed(0)
   {
      strList=&arrStrList[0];
      memset(strList,0,(sizeof (DSTR))*(MAX_TOKEN_COUNT+5));
   };
   ~CSip(){
      
   }
   int parseSip(char *buf, int iBytesReceived, SIP_MSG *sMsg)
   {
      if(iBytesReceived<10)return -1;
      
      if(iBytesReceived>MSG_BUFFER_SIZE)
         iBytesReceived=MSG_BUFFER_SIZE;
      
      sMsg->uiOffset=iBytesReceived;
      if(buf!=&sMsg->rawDataBuffer[0]){
         memset(sMsg->rawDataBuffer,0, sizeof(sMsg->rawDataBuffer));
         memcpy(sMsg->rawDataBuffer, buf, iBytesReceived);
      }
      memcpy((char *)&sMsg->rawDataBuffer[sMsg->uiOffset],MSG_BUFFER_TAIL,MSG_BUFFER_TAIL_SIZE);
      sMsg->uiOffset+=MSG_BUFFER_TAIL_SIZE;
      
      return parseSip(sMsg);
   }
   
   int parseSip(int iBytesReceived, SIP_MSG *sMsg)
   {
      if(iBytesReceived<10)return -1;
      
      sMsg->uiOffset=iBytesReceived;
      memcpy(&sMsg->rawDataBuffer[sMsg->uiOffset],MSG_BUFFER_TAIL,MSG_BUFFER_TAIL_SIZE);
      sMsg->uiOffset+=MSG_BUFFER_TAIL_SIZE;
      
      return parseSip(sMsg);
   }
   
   char *getContent(){return pContent;}
private:
   
   int parseSip(SIP_MSG *sMsg, char *pFrom=NULL, int iHasFirstLine=1)
   {
      
      gspSIPMsg=sMsg;
      
      iSipHasFirstLine=iHasFirstLine;
      if(pFrom==NULL)
      {
         pFrom=(char *)&sMsg->rawDataBuffer[0];
      }
      
      return parse(pFrom);
   }
   
   int parse(char *pFrom);
   
   int parseSipUri(SIP_URI * sipUri, int j,int i, int iIsFirstLine=0);
   int parseFirstLine();
   ///ADD to ren sipUri parse, contact params /contact 
   
   //void printError(char *errStr,int drop);
   //Alert-Info: <http://www.example.com/sounds/moo.wav> ???
   
   //int parseAlso();//parseet vairaakus iespejamos dst
   int parseAccept();
   int parseAcceptEncoding();
   int parseAcceptLanguage();
   int parseAlertInfo();
   int parseAllow();
   int parseAuthenticationInfo();
   //int parseAuthorization();
   
   int parseMF_EXP_CL(DSTR * dstr);
   int parseCallID();
   int parseCallInfo();
   int parseCallRate();
   int parseContact();
   int parseContentDisposition();
   int parseContentEncoding();
   int parseContentLanguage();
  
   int parseContentType();
   int parseCSeq();
   
   int parseDate();
   int parseErrorInfo();
   int parseEvent();
   
   int parseFromTo(HDR_TO_FROM * ft);
 
   int parseInReplyTo();
   
   int parseMIMEVersion();
   int parseMinExpires();
   int parseOrganization();

   int parseP_Asserted_Identity();//   P-Asserted-Identity
   
   int parsePortaBilling();
   int parsePriority();
   //int parseProxyAuthenticate();
   //int parseProxyAuthorization();
   int parseProxyRequire();
   //int parseRequire();
   int parseReason();
   int parseRetryAfter();
   int parseRoutes(HLD_ROUTE * rt);
   /*
    int parseRoute();
    int parseRecordRoute();
    */
   int parseReplayTo();
   int parseServer();
   int parseAut(HDR_AUT * aut);
   
   int parseSubject();
   
   int parseSupportReq(HLD_SUP * sup);
   //int parseSupported();
   int parseTimestamp();
   
   //int parseTo();
   //int parseToOld();
   //int parseUnsupported();
   int parseUserAgent();
   
   int parseVia();
   int parseWarning();
   int parseXSCMessageMeta();
   //int parseWWWAuthenticate();
   
   int parseUnknown();
   
   int contactParams(int i,int j);

   int splitSIP(char *pFrom);
   int tryParseHdr();
   
   SIP_MSG *gspSIPMsg;
   //   int iItems;
   char *pContent;
   DSTR *strList;//[MAX_TOKEN_COUNT];
   
   int iSipHasFirstLine;
   int dropPacket;
   //   int count;
   int n;
   int parsed;
   //int tokenCount;
   int tokensParsed;
   
   static const char  call_id[32];
   static const char  call_rate[32];
   //static char * call_info=     "CALL\rINFO";
   static const char  content_type[32];
   static const char  content_length[32];
   static const char  max_forwards[32];
   static const char  proxy_authen[32];
   static const char  proxy_author[30];
   static const  char  WWW_aut[32];
   static const char  rec_rout[32];
   static const char  p_asserted_id[32];
   
};


#endif //_C_TIVI_SIP_H
