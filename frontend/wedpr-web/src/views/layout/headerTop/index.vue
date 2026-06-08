<template>
  <div class="header-bar-box">
    <div @click="menuToggleClick" class="toggle-con">
      <el-icon v-if="isCollapse" class="icon el-icon-s-unfold" />
      <el-icon v-if="!isCollapse" class="icon el-icon-s-fold" />
    </div>
    <div class="logo-con" v-if="!isCollapse">
      <img class="logo" src="~Assets/images/logo.png" alt="" />
    </div>
    <div class="top-con" v-show="false">
      <div class="header-bar">
        <div class="custom-content-con-box">
          <div class="custom-content-con">
            <div class="info-con">
              <el-dropdown>
                <img class="av" src="~Assets/images/avatar_male.png" />
                <el-dropdown-menu slot="dropdown">
                  <el-dropdown-item @click.native="showModifyPassword">修改密码</el-dropdown-item>
                  <el-dropdown-item @click.native="logOut">退出登录</el-dropdown-item>
                </el-dropdown-menu>
              </el-dropdown>
              {{ userId }}
            </div>
          </div>

        </div>
      </div>
    </div>
  </div>
</template>
<script>
import { mapGetters } from 'vuex'
export default {
  name: 'HeaderBar',
  data() {
    return {
      showModifyModal: false
    }
  },
  props: {
    isCollapse: {
      type: Boolean,
      default: false
    }
  },
  computed: {
    ...mapGetters(['userinfo', 'userId'])
  },
  methods: {
    handleCollpasedChange() {},
    menuToggleClick() {
      this.$emit('menuToggleClick')
    },
    fullScreen() {
      this.$router.push({ path: 'screen' })
    }
  }
}
</script>
<style lang="less" scoped>
.header-bar-box {
  position: relative;
  width: 100%;
  height: 100%;
  display: flex;
  background-color: #f6f8fc;
  width: 100%;
  align-items: center;
  .logo-con {
    height: 100px;
    display: flex;
    align-items: center;
    background-color: rgb(55, 111, 224);
    width: 148px;
    img {
      height: 36px;
      width: auto;
      display: block;
      float: left;
    }
  }
  div.toggle-con {
    display: flex;
    align-items: center;
    width: 68px;
    height: 100%;
    justify-content: center;
    background-color: rgb(55, 111, 224);
    .icon {
      font-size: 26px;
      color: #fff;
    }
  }
  div.top-con {
    padding: 0 50px;
    box-sizing: border-box;
    display: flex;
    align-items: center;
  }
  .header-bar {
    flex: 1;
    display: flex;
    align-items: center;
    height: 100%;
    padding-left: 30px;
    .trans {
      transition: transform 0.2s ease;
    }
    @size: 40px;
    .sider-trigger-a {
      // padding: 6px;
      // width: @size;
      // height: @size;
      display: flex;
      align-items: center;
      text-align: center;
      color: #5c6b77;
      // margin-top: 12px;
      i {
        .trans;
        vertical-align: top;
      }
      &.collapsed i {
        transform: rotateZ(90deg);
        .trans;
      }
    }
  }
  .custom-content-con-box {
    position: absolute;
    right: 24px;
    top: 18px;
    .custom-content-con {
      display: flex;
    }
    .info-con {
      display: flex;
      align-items: center;
      .av {
        width: 32px;
        height: 32px;
        border-radius: 50px;
        margin-right: 8px;
      }
      .full {
        width: 86px;
        height: auto;
        cursor: pointer;
        margin-right: 24px;
      }
    }

    .btn {
      cursor: default;
    }
  }
}
.reset-dialog {
  .el-form-item--mini.el-form-item,
  .el-form-item--small.el-form-item {
    margin-bottom: 0;
  }
}
</style>
