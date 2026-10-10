spring:
  application:
    name: {{service}}

application:
  service:
    service-code: {{service}}

management:
  server:
    port: ${actuator.port:8099}
  endpoint:
    shutdown:
      access: unrestricted
  endpoints:
    web:
      exposure:
        include: health,shutdown
      base-path: /__core
