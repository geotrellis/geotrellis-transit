package geotrellis.transit.services

import javax.servlet._
import javax.servlet.http.HttpServletResponse

class ApiOriginFilter extends Filter {
  @Override
  def doFilter(request:ServletRequest, 
               response:ServletResponse,
               chain:FilterChain) = {
    val res = response.asInstanceOf[HttpServletResponse]
    res.addHeader("Access-Control-Allow-Origin", "*")
    res.addHeader("Access-Control-Allow-Methods", "GET, POST, DELETE, PUT")
    res.addHeader("Access-Control-Allow-Headers", "Content-Type")
    chain.doFilter(request, response)
  }

  @Override
  def destroy() = { }

  @Override
  def init(filterConfig:FilterConfig) = { }
}
