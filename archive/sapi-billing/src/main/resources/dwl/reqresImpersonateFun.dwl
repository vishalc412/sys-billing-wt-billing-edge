%dw 2.0
import * from dw::util::Timer
import isAlpha from dw::core::Strings
import substringAfter from dw::core::Strings
import replaceAll from dw::core::Strings

//substitute X for anything that looks like a path param
fun maskPathSegment(pathSegment) = (if ( !isAlpha(replaceAll(pathSegment,"-","")) ) "X"     else pathSegment)

fun maskPath(reqPath) = (substringAfter(reqPath,"/") splitBy ("/") map ((pathSegment)->           maskPathSegment(pathSegment)     ) joinBy "/")

//because Ping represents ACT differently based on source (myWF/AWP/etc) logic is needed to determine if the token is being impersonated
fun isImpersonated(auth)= (if ( (!(auth.properties.userProperties.act is Object)) and (auth.properties.userProperties.actSub == "null") ) false
	else if ( auth.properties.userProperties.actSub? or auth.properties.userProperties.act.sub? ) true
	else
		false)
		
fun buildactsub(auth)=(if ( auth.properties.userProperties.act is Object ) (auth.properties.userProperties.act.sub default 'EMPTY')
	else 
		(auth.properties.userProperties.actSub default 'EMPTY'))
		
fun buildactEmail(auth)= (if ( auth.properties.userProperties.act is Object ) (auth.properties.userProperties.act.email default 'EMPTY') 
	else
		(auth.properties.userProperties.actEmail default 'EMPTY'))