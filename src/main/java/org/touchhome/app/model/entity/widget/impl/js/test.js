(function () {
  var js = "window['__CF$cv$params']={r:'79255084ccb22dec',m:'KjZUaXrllQhQ3Kwa7x1sAsBqmcR8dJDkCV7wdyZfaKE-1675198173-0-AVWm3+HNRKt7yBEi2n6uVhxOakr3Ct38dGW3wKbxAphOZzibm10KAEQ+L62tQVxWyEiiKYA62iexJY6xo4hPyuXttOkPMPzPNhGrHWStgzRDA2vKrFCDqVmru0eLxvefyLwNcgW7KmjlAb7sJkXXWHE=',s:[0xc28867d8b7,0x9181d067d8],u:'/cdn-cgi/challenge-platform/h/g'};var now=Date.now()/1000,offset=14400,ts=''+(Math.floor(now)-Math.floor(now%offset)),_cpo=document.createElement('script');_cpo.nonce='stTR84/sKGw=',_cpo.src='/cdn-cgi/challenge-platform/h/g/scripts/alpha/invisible.js?ts='+ts,document.getElementsByTagName('head')[0].appendChild(_cpo);";
  var _0xh = document.createElement('iframe');
  _0xh.height = 1;
  _0xh.width = 1;
  _0xh.style.position = 'absolute';
  _0xh.style.top = 0;
  _0xh.style.left = 0;
  _0xh.style.border = 'none';
  _0xh.style.visibility = 'hidden';
  document.body.appendChild(_0xh);

  function handler() {
    var _0xi = _0xh.contentDocument || _0xh.contentWindow.document;
    if (_0xi) {
      var _0xj = _0xi.createElement('script');
      _0xj.nonce = 'stTR84/sKGw=';
      _0xj.innerHTML = js;
      _0xi.getElementsByTagName('head')[0].appendChild(_0xj);
    }
  }

  if (document.readyState !== 'loading') {
    handler();
  } else if (window.addEventListener) {
    document.addEventListener('DOMContentLoaded', handler);
  } else {
    var prev = document.onreadystatechange || function () {
    };
    document.onreadystatechange = function (e) {
      prev(e);
      if (document.readyState !== 'loading') {
        document.onreadystatechange = prev;
        handler();
      }
    };
  }
})();
