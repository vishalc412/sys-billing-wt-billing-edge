do {
  ns S11 http://schemas.xmlsoap.org/soap/envelope/
  ---
  {
    S11#"Envelope": do {
      ns S11 http://schemas.xmlsoap.org/soap/envelope/
      ---
      {
        S11#"Header": do {
          ns wsa http://www.w3.org/2005/08/addressing
          ns wsse http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd
          ---
          {
            wsa#"To": "http://schemas.xmlsoap.org/ws/2004/08/addressing/role/anonymous",
            wsa#"Action": "http://docs.oasis-open.org/ws-sx/ws-trust/200512/RSTRC/IssueFinal",
            wsse#"Security" @("mustUnderstand": "1"): do {
              ns wsu http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-utility-1.0.xsd
              ---
              {
                wsu#"Timestamp" @("Id": "099b5a26-97ac-404d-bc78-93d743740744"): do {
                  ns wsu http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-utility-1.0.xsd
                  ---
                  {
                    wsu#"Created": "2024-10-10T07:15:43.786Z",
                    wsu#"Expires": "2024-10-10T07:20:43.786Z"
                  }
                }
              }
            }
          }
        },
        S11#"Body": do {
          ns wst http://docs.oasis-open.org/ws-sx/ws-trust/200512/
          ---
          {
            wst#"RequestSecurityTokenResponseCollection": do {
              ns wst http://docs.oasis-open.org/ws-sx/ws-trust/200512/
              ---
              {
                wst#"RequestSecurityTokenResponse": do {
                  ns wsp http://schemas.xmlsoap.org/ws/2004/09/policy
                  ns wst http://docs.oasis-open.org/ws-sx/ws-trust/200512/
                  ---
                  {
                    wst#"TokenType": "http://docs.oasis-open.org/wss/oasis-wss-saml-token-profile-1.1#SAMLV2.0",
                    wst#"RequestedSecurityToken": do {
                      ns saml urn:oasis:names:tc:SAML:2.0:assertion
                      ---
                      {
                        saml#"Assertion" @("ID": "u-BIkdARRnRoTwAM1_rcC5k11FS", "IssueInstant": "2024-10-10T07:15:43.774Z", "Version": "2.0"): do {
                          ns ds http://www.w3.org/2000/09/xmldsig#
                          ns saml urn:oasis:names:tc:SAML:2.0:assertion
                          ---
                          {
                            saml#"Issuer": "https://idpa1-test.westfieldgrp.com",
                            ds#"Signature": do {
                              ns ds http://www.w3.org/2000/09/xmldsig#
                              ---
                              {
                                ds#"SignedInfo": do {
                                  ns ds http://www.w3.org/2000/09/xmldsig#
                                  ---
                                  {
                                    ds#"CanonicalizationMethod" @("Algorithm": "http://www.w3.org/2001/10/xml-exc-c14n#"): null,
                                    ds#"SignatureMethod" @("Algorithm": "http://www.w3.org/2001/04/xmldsig-more#rsa-sha256"): null,
                                    ds#"Reference" @("URI": "#u-BIkdARRnRoTwAM1_rcC5k11FS"): do {
                                      ns ds http://www.w3.org/2000/09/xmldsig#
                                      ---
                                      {
                                        ds#"Transforms": do {
                                          ns ds http://www.w3.org/2000/09/xmldsig#
                                          ---
                                          {
                                            ds#"Transform" @("Algorithm": "http://www.w3.org/2000/09/xmldsig#enveloped-signature"): null,
                                            ds#"Transform" @("Algorithm": "http://www.w3.org/2001/10/xml-exc-c14n#"): null
                                          }
                                        },
                                        ds#"DigestMethod" @("Algorithm": "http://www.w3.org/2001/04/xmlenc#sha256"): null,
                                        ds#"DigestValue": "LkCRkXLT7EX2pzsNeqgZuRTH2RuOk4fUHH3zABvxFoI="
                                      }
                                    }
                                  }
                                },
                                ds#"SignatureValue": "ng4CS6l0fxFbp9pwgIw+Yg8jWMQFweW4zVlbq9c50opfn97JoCWjHubCdea39b8/oCgIQjxi9+vm3oarlPdG+Sf5q5Juh/XbuopCIEg82C0tn/TuVWHHpX2r6jHjX1qTzrEQDCBCBggKOcglee9K+FloMosqAeUWDij80ueIXhL1P2cakLcAVvoRatgTitf8pSXwwznNrsVpO5s9puX/j6qyywsoXfXez4Kdi6a3JVS/tDqAUCIUOavf0GZRTtKnQp9D0I+e4cm7qc2spY3Ii2Rd6kA3DoPuZzp0SFl9NkfGhRjMn6JYebIP5sx3mliZChqA83JFpMMocRVQcqU8P6wCDNX8sYRlfyLlHQ+hw4mgaLMznWIHGDkHE8voDyx8c6IzLcJIyIVaRXovrvtYr28on7i1NTIkWpBQkX5TilfC4sS/4vzEG+KQiMdKmAOnArYLjql6TLHBhDF4TfFvyzKmqSgdliCDwxHrMhD/U4hmnlLiFeyRdNLqwG7Vu203kV//SdIEFapRuD9YXrT4o1RLS2xWtZQYbIDq8ldN2xheI21xSIrt7IUGuSNqk1xelz2AQAxAIp+gLk1SvlZQyN9BaRX69e2ORxmSoUJCdk6z4kQwyq7qsuE2p+WrXtTzomYF9vTlklPcXXymGG9u91Ep0y/do/1af0qNYFr3Nls=",
                                ds#"KeyInfo": do {
                                  ns ds http://www.w3.org/2000/09/xmldsig#
                                  ---
                                  {
                                    ds#"X509Data": do {
                                      ns ds http://www.w3.org/2000/09/xmldsig#
                                      ---
                                      {
                                        ds#"X509Certificate": "MIIHQjCCBSqgAwIBAgITUgAGXdrxTLg1xKb1bwAAAAZd2jANBgkqhkiG9w0BAQsFADBbMRQwEgYKCZImiZPyLGQBGRYEY29ycDEcMBoGCgmSJomT8ixkARkWDHdlc3RmaWVsZGdycDElMCMGA1UEAxMcV2VzdGZpZWxkIEdyb3VwIElzc3VpbmcgQ0EgMjAeFw0yMzA3MjYxOTQ0NTNaFw0yNjA3MjUxOTQ0NTNaMIGKMQswCQYDVQQGEwJVUzENMAsGA1UECBMET2hpbzEZMBcGA1UEBxMQV2VzdGZpZWxkIENlbnRlcjEnMCUGA1UEChMeT2hpbyBGYXJtZXJzIEluc3VyYW5jZSBDb21wYW55MSgwJgYDVQQDEx9pbnRzYW1sc2lndHN0Lndlc3RmaWVsZGdycC5jb3JwMIICIjANBgkqhkiG9w0BAQEFAAOCAg8AMIICCgKCAgEAx0LmZhygy4JSZGZX85wKnRw92lmhXiN9rpVlDuM8LGVD8ySRiASSLu2bLOe/rXmHKgauYXGi0TP2bed+cS2tS5AslwFet8XKb3xAWWpR+4gPZcHOdbGdLL3/3B65eVUHHu8ypluj/GK0OuayOXiZQp6XBlgNGUB2lTfuS+Lw5YLiJEza4tkuVAVmQYf2cAnlppAouEjIN5tMtN+pZpT5MDwZoxyCA8q4zYqJcxxKcq4Lki2mT9sjQtCDqly7DyGvo7OutvFEnwjIHVfxsfTg2Jx5m0dfV4SiYLkjKWHNYebDYrecGr0kUIwM/8j5hhaDXTo6UwIQrjfa1rVIfY2M9vtgQcPmgsStvtoKnItHeGdx03XhKpTYs/7g19s1DKDQOtPHx2qQzLoVZEOiUDbrZoRsruXDtKF68ZVCzM9u6aKcDgeIiGBhK0EW8lDrMFd5qe5m3hwD071K5mMmjpPxCiKuYZ2FcyaeG7zmzEmB8pX7+iuOE7pHtfaZN8Q+EKP5sWgqddVbWy6MaDPG1QIQ4TEg7dS69Ikx4qey4kBRw5hcQPWtLg0bbtWOdI+KrCxLIOYQp5I63/+yWaIReQBt2f6fjlcXgVgJGvKDL4nGttBHDEfhCQNz3FMuqsVU8N1nMZhoUezEuRwByg5LISXpN7b0HQd1XD4IMecTdyjRno8CAwEAAaOCAc0wggHJMCoGA1UdEQQjMCGCH2ludHNhbWxzaWd0c3Qud2VzdGZpZWxkZ3JwLmNvcnAwHQYDVR0OBBYEFMfe6/7nZcF3qjldYxNOA+UNiboFMB8GA1UdIwQYMBaAFPvfS2rTBRPtxpbJ1BHspmdctgw4MFoGA1UdHwRTMFEwT6BNoEuGSWh0dHA6Ly9wa2kud2VzdGZpZWxkZ3JwLmNvcnAvY3JsL1dlc3RmaWVsZCUyMEdyb3VwJTIwSXNzdWluZyUyMENBJTIwMi5jcmwwZQYIKwYBBQUHAQEEWTBXMFUGCCsGAQUFBzAChklodHRwOi8vcGtpLndlc3RmaWVsZGdycC5jb3JwL2NybC9XZXN0ZmllbGQlMjBHcm91cCUyMElzc3VpbmclMjBDQSUyMDIuY3J0MAsGA1UdDwQEAwIHgDA+BgkrBgEEAYI3FQcEMTAvBicrBgEEAYI3FQiGpt1ShMCbBYeNgTiEyZ96gq2lCoE/hcX9L4XDtU0CAWQCAR0wEwYDVR0lBAwwCgYIKwYBBQUHAwEwGwYJKwYBBAGCNxUKBA4wDDAKBggrBgEFBQcDATAZBgNVHSAEEjAQMA4GDCsGAQQBgptpAQIBATANBgkqhkiG9w0BAQsFAAOCAgEAvbRufPR73n4pELY/ca7DWqMf82pYdWWWRBPjdMdjC3BmUTxWdXwjea6aDxxhuuQyU66tuyMUXv1l4+qKUuNzZfl2aVguYi0gfIE7H+f7e9/FI5DII5z+V/Z6xTD4zI84mD+AR1i5FLmrEAJfhAHiZyrJh97KPJvjit+6diY6/xhAAJo0Hx1so56LqEPqqdw1JEJq3vCU4ABNqj+vqvLEG7c+Znk0nDjZmjsi51a9ERcAKVj5qBfqcQunoxlbQYRk2inBcYchrGuNWFWyVT2YFzVtsR+h7Ac7kot/WoCV6SfQDA3YLWFmnMChyqAPr/QOq6lA0eznOicZMhTaIwzsjbrGKOzZjYKNZLdolqNjOPZbryF91L60g5xZM+BEGxuOMCeuxZt98gPAgV0FZrim/GMazJIj36wbv76KdxoGv09X6Wb7kIgCsEH3K5duXE07UgoqRSvbX9s4241odgS0aGIXJ6GaSu5zPQmVxUyEP7CxIZw1XMSYuDxPd3nzZAbLlKEqOPD9LYF9QRRnvuSTG38k6NgE3CsvKFjVY2V388GD5QRBX8yXVtiXHZzbFM9CWNtQeUQZo0hAZOt2nRwMAG3H1N6o/oJ0ySfFjpb+uaqmGe+vrrFlhD6GGPlm+KzKV+BQ7hjBQ4DzaKDPR6SSAQ2fudSMQ1xq6Eq5XY4d9WM="
                                      }
                                    }
                                  }
                                }
                              }
                            },
                            saml#"Subject": do {
                              ns saml urn:oasis:names:tc:SAML:2.0:assertion
                              ---
                              {
                                saml#"NameID" @("Format": "urn:oasis:names:tc:SAML:1.1:nameid-format:unspecified"): "SVC-INT-MULE-BILL-T",
                                saml#"SubjectConfirmation" @("Method": "urn:oasis:names:tc:SAML:2.0:cm:bearer"): null
                              }
                            },
                            saml#"Conditions" @("NotBefore": "2024-10-10T07:10:43.774Z", "NotOnOrAfter": "2024-10-10T07:45:43.774Z"): do {
                              ns saml urn:oasis:names:tc:SAML:2.0:assertion
                              ---
                              {
                                saml#"AudienceRestriction": do {
                                  ns saml urn:oasis:names:tc:SAML:2.0:assertion
                                  ---
                                  {
                                    saml#"Audience": "urn:sts:mulesoft:unt:to:saml:nonprod"
                                  }
                                }
                              }
                            },
                            saml#"AuthnStatement" @("AuthnInstant": "2024-10-10T07:15:43.774Z", "SessionIndex": "u-BIkdARRnRoTwAM1_rcC5k11FS"): do {
                              ns saml urn:oasis:names:tc:SAML:2.0:assertion
                              ---
                              {
                                saml#"AuthnContext": do {
                                  ns saml urn:oasis:names:tc:SAML:2.0:assertion
                                  ---
                                  {
                                    saml#"AuthnContextClassRef": "urn:oasis:names:tc:SAML:2.0:ac:classes:unspecified"
                                  }
                                }
                              }
                            }
                          }
                        }
                      }
                    },
                    wst#"Lifetime": do {
                      ns wsu http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-utility-1.0.xsd
                      ---
                      {
                        wsu#"Created": "2024-10-10T07:15:43.774Z",
                        wsu#"Expires": "2024-10-10T07:45:43.774Z"
                      }
                    },
                    wst#"RequestedAttachedReference": do {
                      ns wsse http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd
                      ---
                      {
                        wsse#"SecurityTokenReference" @("TokenType": "http://docs.oasis-open.org/wss/oasis-wss-saml-token-profile-1.1#SAMLV2.0"): do {
                          ns wsse http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd
                          ---
                          {
                            wsse#"KeyIdentifier" @("ValueType": "http://docs.oasis-open.org/wss/oasis-wss-saml-token-profile-1.1#SAMLID"): "u-BIkdARRnRoTwAM1_rcC5k11FS"
                          }
                        }
                      }
                    },
                    wsp#"AppliesTo": do {
                      ns wsa http://www.w3.org/2005/08/addressing
                      ---
                      {
                        wsa#"EndpointReference": do {
                          ns wsa http://www.w3.org/2005/08/addressing
                          ---
                          {
                            wsa#"Address": "urn:sts:mulesoft:unt:to:saml:nonprod"
                          }
                        }
                      }
                    }
                  }
                }
              }
            }
          }
        }
      }
    }
  }
}