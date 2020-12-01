package com.linkedin.venice.acl.handler;

import com.linkedin.venice.acl.AclCreationDeletionListener;
import com.linkedin.venice.acl.AclException;
import com.linkedin.venice.acl.DynamicAccessController;
import com.linkedin.venice.exceptions.VeniceException;
import com.linkedin.venice.exceptions.VeniceNoStoreException;
import com.linkedin.venice.meta.ReadOnlyStoreRepository;
import com.linkedin.venice.meta.Store;
import com.linkedin.venice.utils.NettyUtils;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.ssl.SslHandler;
import io.netty.util.ReferenceCountUtil;
import java.net.URI;
import java.security.cert.X509Certificate;
import java.util.stream.Collectors;
import javax.net.ssl.SSLPeerUnverifiedException;
import org.apache.log4j.Logger;


/**
 * Store-level access control handler, which is being used by both Router and Server.
 */
@ChannelHandler.Sharable
public class StoreAclHandler extends SimpleChannelInboundHandler<HttpRequest> {
  private static final Logger logger = Logger.getLogger(StoreAclHandler.class);

  private final ReadOnlyStoreRepository metadataRepository;
  private final DynamicAccessController accessController;

  public StoreAclHandler(DynamicAccessController accessController,
      ReadOnlyStoreRepository metadataRepository) {
    this.metadataRepository = metadataRepository;
    this.accessController = accessController.init(
        metadataRepository.getAllStores().stream().map(Store::getName).collect(Collectors.toList()));
    this.metadataRepository.registerStoreDataChangedListener(
        new AclCreationDeletionListener(accessController));
  }

  /**
   * Verify if client has permission to access.
   *
   * @param ctx
   * @param req
   * @throws SSLPeerUnverifiedException
   */
  @Override
  public void channelRead0(ChannelHandlerContext ctx, HttpRequest req) throws SSLPeerUnverifiedException {
    X509Certificate clientCert = (X509Certificate) ctx.pipeline().get(SslHandler.class).engine().getSession().getPeerCertificates()[0];

    String uri = req.uri();
    // Parse resource type and store name
    String[] requestParts = URI.create(uri).getPath().split("/");
    if (requestParts.length < 3) {
      NettyUtils.setupResponseAndFlush(HttpResponseStatus.BAD_REQUEST, ("Invalid request  uri: " + uri).getBytes(), false, ctx);
      return;
    }
    String storeName = requestParts[2];

    String method = req.method().name();
    try {
      Store store = metadataRepository.getStoreOrThrow(storeName);
      if (store.isSystemStore()) {
        // Ignore ACL for Venice system stores. System stores should be world readable and only contain public information.
        ReferenceCountUtil.retain(req);
        ctx.fireChannelRead(req);
      } else {
        try {
          if (accessController.hasAccess(clientCert, storeName, method)) {
            // Client has permission. Proceed
            ReferenceCountUtil.retain(req);
            ctx.fireChannelRead(req);
          } else {
            // Fact:
            //   Request gets rejected.
            // Possible Reasons:
            //   A. ACL not found. OR,
            //   B. ACL exists but caller does not have permission.

            String client = ctx.channel().remoteAddress().toString(); //ip and port
            String errLine = String.format("%s requested %s %s", client, method, req.uri());

            if (!accessController.isFailOpen() && !accessController.hasAcl(storeName)) {  // short circuit, order matters
              // Case A
              // Conditions:
              //   0. (outside) Store exists and is being access controlled. AND,
              //   1. (left) The following policy is applied: if ACL not found, reject the request. AND,
              //   2. (right) ACL not found.
              // Result:
              //   Request is rejected by DynamicAccessController#hasAccess()
              // Root cause:
              //   Requested resource exists but does not have ACL.
              // Action:
              //   return 401 Unauthorized
              logger.warn("Requested store does not have ACL: " + errLine);
              logger.debug("Existing stores: "
                  + metadataRepository.getAllStores().stream().map(Store::getName).sorted().collect(Collectors.toList()));
              logger.debug("Access-controlled stores: "
                  + accessController.getAccessControlledResources().stream().sorted().collect(Collectors.toList()));
              NettyUtils.setupResponseAndFlush(HttpResponseStatus.UNAUTHORIZED,
                  ("ACL not found!\n"
                      + "Either it has not been created, or can not be loaded.\n"
                      + "Please create the ACL, or report the error if you know for sure that ACL exists for this store: "
                      + storeName).getBytes(), false, ctx);
            } else {
              // Case B
              // Conditions:
              //   1. Fail closed, and ACL found. OR,
              //   2. Fail open, and ACL found. OR,
              //   3. Fail open, and ACL not found.
              // Analyses:
              //   (1) ACL exists, therefore result is determined by ACL.
              //       Since the request has been rejected, it must be due to lack of permission.
              //   (2) ACL exists, therefore result is determined by ACL.
              //       Since the request has been rejected, it must be due to lack of permission.
              //   (3) In such case, request would NOT be rejected in the first place,
              //       according to the definition of hasAccess() in DynamicAccessController interface.
              //       Contradiction to the fact, therefore this case is impossible.
              // Root cause:
              //   Caller does not have permission to access the resource.
              // Action:
              //   return 403 Forbidden
              logger.debug("Unauthorized access rejected: " + errLine);
              NettyUtils.setupResponseAndFlush(HttpResponseStatus.FORBIDDEN,
                  ("Access denied!\n"
                      + "If you are the store owner, add this application (or your own username for Venice shell client) to the store ACL.\n"
                      + "Otherwise, ask the store owner for read permission.").getBytes(), false, ctx);
            }
          }
        } catch (AclException e) {
          String client = ctx.channel().remoteAddress().toString(); //ip and port
          String errLine = String.format("%s requested %s %s", client, method, req.uri());

          if (accessController.isFailOpen()) {
            logger.warn("Exception occurred! Access granted: " + errLine + "\n" + e);
            ReferenceCountUtil.retain(req);
            ctx.fireChannelRead(req);
          } else {
            logger.warn("Exception occurred! Access rejected: " + errLine + "\n" + e);
            NettyUtils.setupResponseAndFlush(HttpResponseStatus.FORBIDDEN, new byte[0], false, ctx);
          }
        }
      }
    } catch (VeniceNoStoreException noStoreException) {
      String client = ctx.channel().remoteAddress().toString(); //ip and port
      String errLine = String.format("%s requested %s %s", client, method, req.uri());
      logger.debug("Requested store does not exist: " + errLine);
      NettyUtils.setupResponseAndFlush(HttpResponseStatus.BAD_REQUEST,
          ("Invalid Venice store name: " + storeName).getBytes(), false, ctx);
    }
  }
}