"""Shared pieces: the error that becomes an HTTP reply, logging, and the public-address check."""
from __future__ import annotations

import ipaddress
import socket
import urllib.parse
import urllib.request


class Problem(Exception):
    """Ends a request with this HTTP status and {"error": message}. The apps show the message to the user."""

    def __init__(self, status: int, message: str):
        super().__init__(message)
        self.status = status


def log(*parts):
    print(*parts, flush=True)


def public_host(url: str) -> bool:
    """True when every address the URL's host resolves to is a public one (no LAN, loopback or link-local).
    Photos and videos come from the public web, and the server must not be steered at the local network."""
    host = urllib.parse.urlsplit(url).hostname
    if not host:
        return False
    try:
        addrs = {a[4][0] for a in socket.getaddrinfo(host, None)}
    except OSError:
        return False
    return all(ipaddress.ip_address(a.split("%")[0]).is_global for a in addrs)


class PublicOnlyRedirects(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        if not newurl.startswith(("http://", "https://")) or not public_host(newurl):
            raise OSError("redirect to a non-public address")
        return super().redirect_request(req, fp, code, msg, headers, newurl)
